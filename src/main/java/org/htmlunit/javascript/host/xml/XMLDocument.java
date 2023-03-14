/*
 * Copyright (c) 2002-2023 Gargoyle Software Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.htmlunit.javascript.host.xml;

import static org.htmlunit.BrowserVersionFeatures.JS_DOMPARSER_EMPTY_STRING_IS_ERROR;
import static org.htmlunit.BrowserVersionFeatures.JS_DOMPARSER_EXCEPTION_ON_ERROR;
import static org.htmlunit.BrowserVersionFeatures.JS_DOMPARSER_PARSERERROR_ON_ERROR;
import static org.htmlunit.BrowserVersionFeatures.JS_XML_GET_ELEMENTS_BY_TAG_NAME_LOCAL;
import static org.htmlunit.javascript.configuration.SupportedBrowser.CHROME;
import static org.htmlunit.javascript.configuration.SupportedBrowser.EDGE;
import static org.htmlunit.javascript.configuration.SupportedBrowser.FF;
import static org.htmlunit.javascript.configuration.SupportedBrowser.FF_ESR;

import java.io.IOException;
import java.io.Serializable;
import java.util.function.Predicate;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import org.htmlunit.StringWebResponse;
import org.htmlunit.WebResponse;
import org.htmlunit.WebWindow;
import org.htmlunit.html.DomAttr;
import org.htmlunit.html.DomElement;
import org.htmlunit.html.DomNode;
import org.htmlunit.html.HtmlElement;
import org.htmlunit.javascript.HtmlUnitScriptable;
import org.htmlunit.javascript.JavaScriptEngine;
import org.htmlunit.javascript.configuration.JsxClass;
import org.htmlunit.javascript.configuration.JsxConstructor;
import org.htmlunit.javascript.configuration.JsxFunction;
import org.htmlunit.javascript.host.Element;
import org.htmlunit.javascript.host.dom.Attr;
import org.htmlunit.javascript.host.dom.DOMException;
import org.htmlunit.javascript.host.dom.Document;
import org.htmlunit.javascript.host.html.HTMLCollection;
import org.htmlunit.svg.SvgElement;
import org.htmlunit.xml.XmlPage;

import net.sourceforge.htmlunit.corejs.javascript.Context;

/**
 * A JavaScript object for {@code XMLDocument}.
 *
 * @author Ahmed Ashour
 * @author Marc Guillemot
 * @author Sudhan Moghe
 * @author Ronald Brill
 * @author Chuck Dumont
 * @author Frank Danek
 */
@JsxClass
public class XMLDocument extends Document {

    private static final Log LOG = LogFactory.getLog(XMLDocument.class);

    /**
     * Creates a new instance.
     */
    @JsxConstructor({CHROME, EDGE, FF, FF_ESR})
    public XMLDocument() {
        this(null);
    }

    /**
     * Creates a new instance, with associated XmlPage.
     * @param enclosingWindow the window
     */
    public XMLDocument(final WebWindow enclosingWindow) {
        if (enclosingWindow != null) {
            try {
                final XmlPage page = new XmlPage((WebResponse) null, enclosingWindow);
                setDomNode(page);
            }
            catch (final IOException e) {
                throw Context.reportRuntimeError("IOException: " + e);
            }
        }
    }

    /**
     * Loads an XML document using the supplied string.
     *
     * @param strXML A string containing the XML string to load into this XML document object
     *        This string can contain an entire XML document or a well-formed fragment.
     * @return true if the load succeeded; false if the load failed
     */
    public boolean loadXML(final String strXML) {
        final WebWindow webWindow = getWindow().getWebWindow();
        try {
            if (StringUtils.isEmpty(strXML) && getBrowserVersion().hasFeature(JS_DOMPARSER_EMPTY_STRING_IS_ERROR)) {
                throw new IOException("Error parsing XML '" + strXML + "'");
            }

            final WebResponse webResponse = new StringWebResponse(strXML, webWindow.getEnclosedPage().getUrl());

            final XmlPage page = new XmlPage(webResponse, webWindow, false);
            setDomNode(page);
            return true;
        }
        catch (final IOException e) {
            if (LOG.isDebugEnabled()) {
                LOG.debug("Error parsing XML\n" + strXML, e);
            }

            if (getBrowserVersion().hasFeature(JS_DOMPARSER_EXCEPTION_ON_ERROR)) {
                throw asJavaScriptException(
                        new DOMException("Syntax Error",
                            DOMException.SYNTAX_ERR));
            }
            if (getBrowserVersion().hasFeature(JS_DOMPARSER_PARSERERROR_ON_ERROR)) {
                try {
                    final XmlPage page = createParserErrorXmlPage("Syntax Error", webWindow);
                    setDomNode(page);
                }
                catch (final IOException eI) {
                    LOG.error("Could not handle ParserError", e);
                }
            }

            return false;
        }
    }

    private static XmlPage createParserErrorXmlPage(final String message, final WebWindow webWindow)
            throws IOException {
        final String xml = "<parsererror xmlns=\"http://www.mozilla.org/newlayout/xml/parsererror.xml\">\n"
            + message + "\n"
            + "<sourcetext></sourcetext>\n"
            + "</parsererror>";

        final WebResponse webResponse = new StringWebResponse(xml, webWindow.getEnclosedPage().getUrl());

        return new XmlPage(webResponse, webWindow, false);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public HtmlUnitScriptable makeScriptableFor(final DomNode domNode) {
        final HtmlUnitScriptable scriptable;

        // TODO: cleanup, getScriptObject() should be used!!!
        if (domNode instanceof DomElement && !(domNode instanceof HtmlElement)) {
            if (domNode instanceof SvgElement) {
                final Class<? extends HtmlUnitScriptable> javaScriptClass
                    = ((JavaScriptEngine) getWindow().getWebWindow().getWebClient()
                        .getJavaScriptEngine()).getJavaScriptClass(domNode.getClass());
                try {
                    scriptable = javaScriptClass.newInstance();
                }
                catch (final Exception e) {
                    throw Context.throwAsScriptRuntimeEx(e);
                }
            }
            else {
                scriptable = new Element();
            }
        }
        else if (domNode instanceof DomAttr) {
            scriptable = new Attr();
        }
        else {
            return super.makeScriptableFor(domNode);
        }

        scriptable.setPrototype(getPrototype(scriptable.getClass()));
        scriptable.setParentScope(getParentScope());
        scriptable.setDomNode(domNode);
        return scriptable;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected void initParentScope(final DomNode domNode, final HtmlUnitScriptable scriptable) {
        scriptable.setParentScope(getParentScope());
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @JsxFunction
    public HTMLCollection getElementsByTagName(final String tagName) {
        final DomNode firstChild = getDomNodeOrDie().getFirstChild();
        if (firstChild == null) {
            return HTMLCollection.emptyCollection(getWindow().getDomNodeOrDie());
        }

        final HTMLCollection elements = new HTMLCollection(getDomNodeOrDie(), false);

        elements.setIsMatchingPredicate(
                (Predicate<DomNode> & Serializable)
                node -> {
                    final String nodeName;
                    if (getBrowserVersion().hasFeature(JS_XML_GET_ELEMENTS_BY_TAG_NAME_LOCAL)) {
                        nodeName = node.getLocalName();
                    }
                    else {
                        nodeName = node.getNodeName();
                    }

                    return nodeName.equals(tagName);
                });
        return elements;
    }
}