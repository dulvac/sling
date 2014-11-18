/*******************************************************************************
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 ******************************************************************************/

package org.apache.sling.scripting.sightly.engine.extension;

import java.util.HashSet;
import java.util.Set;
import java.util.regex.Pattern;

import javax.script.Bindings;

import org.apache.felix.scr.annotations.Component;
import org.apache.felix.scr.annotations.Properties;
import org.apache.felix.scr.annotations.Property;
import org.apache.felix.scr.annotations.Service;
import org.apache.sling.api.SlingHttpServletRequest;
import org.apache.sling.scripting.sightly.api.ExtensionInstance;
import org.apache.sling.scripting.sightly.api.RuntimeExtensionComponent;
import org.apache.sling.xss.XSSAPI;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.apache.sling.scripting.sightly.api.RenderContext;
import org.apache.sling.scripting.sightly.api.RuntimeExtension;
import org.apache.sling.scripting.sightly.api.RuntimeExtensionException;
import org.apache.sling.scripting.sightly.compiler.api.CompilerException;
import org.apache.sling.scripting.sightly.compiler.api.plugin.MarkupContext;
import org.apache.sling.scripting.sightly.filter.XSSFilter;
import org.apache.sling.scripting.sightly.html.MarkupUtils;

/**
 * Runtime support for XSS filtering
 */
@Component
@Service(RuntimeExtension.class)
@Properties(
        @Property(name = RuntimeExtensionComponent.SCR_PROP_NAME, value = XSSFilter.FUNCTION_NAME)
)
public class XSSRuntimeExtension extends RuntimeExtensionComponent {

    private static final Set<String> elementNameWhiteList = new HashSet<String>();
    private static final Logger log = LoggerFactory.getLogger(XSSRuntimeExtension.class);

    private static final Pattern VALID_ATTRIBUTE = Pattern.compile("^[a-zA-Z_:][\\-a-zA-Z0-9_:\\.]*$");


    @Override
    public ExtensionInstance provide(final RenderContext runtimeContext) {

        final XSSAPI xssapi = obtainAPI(runtimeContext.getBindings());

        return new ExtensionInstance() {
            @Override
            public Object call(Object... arguments) {
                if (arguments.length < 2) {
                    throw new RuntimeExtensionException(String.format("Extension %s requires at least %d arguments", name(), 2));
                }
                Object original = arguments[0];
                Object option = arguments[1];
                Object hint = null;
                if (arguments.length >= 3) {
                    hint = arguments[2];
                }
                MarkupContext markupContext = null;
                if (option != null && option instanceof String) {
                    String name = (String) option;
                    markupContext = MarkupContext.lookup(name);
                }
                if (markupContext == MarkupContext.UNSAFE) {
                    return original;
                }
                if (markupContext == null) {
                    log.warn("Expression context {} is invalid, expression will be replaced by the empty string", option);
                    return "";
                }
                String text = runtimeContext.getObjectModel().coerceToString(original);
                return applyXSSFilter(text, hint, markupContext);
            }

            private String applyXSSFilter(String text, Object hint, MarkupContext xssContext) {
                if (xssContext.equals(MarkupContext.ATTRIBUTE) && hint instanceof String) {
                    String attributeName = (String) hint;
                    MarkupContext attrMarkupContext = getAttributeMarkupContext(attributeName);
                    return applyXSSFilter(text, attrMarkupContext);
                }
                return applyXSSFilter(text, xssContext);
            }

            private String applyXSSFilter(String text, MarkupContext xssContext) {
                if (xssContext.equals(MarkupContext.ATTRIBUTE)) {
                    return xssapi.encodeForHTMLAttr(text);
                }
                if (xssContext.equals(MarkupContext.COMMENT) ||
                        xssContext.equals(MarkupContext.TEXT)) {
                    return xssapi.encodeForHTML(text);
                }
                if (xssContext.equals(MarkupContext.ATTRIBUTE_NAME)) {
                    return escapeAttributeName(text);
                }
                if (xssContext.equals(MarkupContext.NUMBER)) {
                    return xssapi.getValidLong(text, 0).toString();
                }
                if (xssContext.equals(MarkupContext.URI)) {
                    return xssapi.getValidHref(text);
                }
                if (xssContext.equals(MarkupContext.SCRIPT_TOKEN)
                        || xssContext.equals(MarkupContext.SCRIPT_COMMENT)) {
                    return xssapi.getValidJSToken(text, "");
                }
                if (xssContext.equals(MarkupContext.SCRIPT_STRING)
                        || xssContext.equals(MarkupContext.STYLE_STRING)) {
                    return xssapi.encodeForJSString(text);
                }
                if (xssContext.equals(MarkupContext.ELEMENT_NAME)) {
                    return escapeElementName(text);
                }
                if (xssContext.equals(MarkupContext.HTML)) {
                    return xssapi.filterHTML(text);
                }
                return text; //todo: apply the rest of XSS filters
            }
        };
    }

    private String escapeElementName(String original) {
        original = original.trim();
        if (elementNameWhiteList.contains(original.toLowerCase())) {
            return original;
        }
        return "";
    }

    private XSSAPI obtainAPI(Bindings bindings) {
        SlingHttpServletRequest request = (SlingHttpServletRequest) bindings.get("request");
        if (request == null) {
            throw new CompilerException("Cannot obtain request from bindings");
        }
        return request.adaptTo(XSSAPI.class);
    }

    private MarkupContext getAttributeMarkupContext(String attributeName) {
        if ("src".equalsIgnoreCase(attributeName) || "href".equalsIgnoreCase(attributeName)) {
            return MarkupContext.URI;
        }
        return MarkupContext.ATTRIBUTE;
    }

    private String escapeAttributeName(String attributeName) {
        if (attributeName == null) {
            return null;
        }
        attributeName = attributeName.trim();
        if (matchPattern(VALID_ATTRIBUTE, attributeName) && !MarkupUtils.isSensitiveAttribute(attributeName)) {
            return attributeName;
        }
        return null;
    }

    private boolean matchPattern(Pattern pattern, String str) {
        return pattern.matcher(str).matches();
    }

    static {
        elementNameWhiteList.add("section");
        elementNameWhiteList.add("nav");
        elementNameWhiteList.add("article");
        elementNameWhiteList.add("aside");
        elementNameWhiteList.add("h1");
        elementNameWhiteList.add("h2");
        elementNameWhiteList.add("h3");
        elementNameWhiteList.add("h4");
        elementNameWhiteList.add("h5");
        elementNameWhiteList.add("h6");
        elementNameWhiteList.add("header");
        elementNameWhiteList.add("footer");
        elementNameWhiteList.add("address");
        elementNameWhiteList.add("main");
        elementNameWhiteList.add("p");
        elementNameWhiteList.add("pre");
        elementNameWhiteList.add("blockquote");
        elementNameWhiteList.add("ol");
        elementNameWhiteList.add("li");
        elementNameWhiteList.add("dl");
        elementNameWhiteList.add("dt");
        elementNameWhiteList.add("dd");
        elementNameWhiteList.add("figure");
        elementNameWhiteList.add("figcaption");
        elementNameWhiteList.add("div");
        elementNameWhiteList.add("a");
        elementNameWhiteList.add("em");
        elementNameWhiteList.add("strong");
        elementNameWhiteList.add("small");
        elementNameWhiteList.add("s");
        elementNameWhiteList.add("cite");
        elementNameWhiteList.add("q");
        elementNameWhiteList.add("dfn");
        elementNameWhiteList.add("abbbr");
        elementNameWhiteList.add("data");
        elementNameWhiteList.add("time");
        elementNameWhiteList.add("code");
        elementNameWhiteList.add("var");
        elementNameWhiteList.add("samp");
        elementNameWhiteList.add("kbd");
        elementNameWhiteList.add("sub");
        elementNameWhiteList.add("sup");
        elementNameWhiteList.add("i");
        elementNameWhiteList.add("b");
        elementNameWhiteList.add("u");
        elementNameWhiteList.add("mark");
        elementNameWhiteList.add("ruby");
        elementNameWhiteList.add("rt");
        elementNameWhiteList.add("rp");
        elementNameWhiteList.add("bdi");
        elementNameWhiteList.add("bdo");
        elementNameWhiteList.add("span");
        elementNameWhiteList.add("br");
        elementNameWhiteList.add("wbr");
        elementNameWhiteList.add("ins");
        elementNameWhiteList.add("del");
        elementNameWhiteList.add("table");
        elementNameWhiteList.add("caption");
        elementNameWhiteList.add("colgroup");
        elementNameWhiteList.add("col");
        elementNameWhiteList.add("tbody");
        elementNameWhiteList.add("thead");
        elementNameWhiteList.add("tfoot");
        elementNameWhiteList.add("tr");
        elementNameWhiteList.add("td");
        elementNameWhiteList.add("th");
    }

}