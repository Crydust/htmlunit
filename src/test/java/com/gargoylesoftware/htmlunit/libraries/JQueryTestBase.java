/*
 * Copyright (c) 2002-2021 Gargoyle Software Inc.
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
package com.gargoylesoftware.htmlunit.libraries;

import static com.gargoylesoftware.htmlunit.runners.BrowserVersionClassRunner.NO_ALERTS_DEFINED;
import static org.junit.Assert.fail;

import java.io.IOException;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import org.eclipse.jetty.server.Server;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.ComparisonFailure;
import org.junit.runner.RunWith;
import org.openqa.selenium.By;
import org.openqa.selenium.NoSuchElementException;
import org.openqa.selenium.StaleElementReferenceException;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.htmlunit.HtmlUnitDriver;

import com.gargoylesoftware.htmlunit.WebClient;
import com.gargoylesoftware.htmlunit.WebDriverTestCase;
import com.gargoylesoftware.htmlunit.WebRequest;
import com.gargoylesoftware.htmlunit.WebResponse;
import com.gargoylesoftware.htmlunit.WebResponseData;
import com.gargoylesoftware.htmlunit.WebServerTestCase;
import com.gargoylesoftware.htmlunit.junit.BrowserRunner;
import com.gargoylesoftware.htmlunit.util.NameValuePair;
import com.gargoylesoftware.htmlunit.util.WebConnectionWrapper;

/**
 * Base class for jQuery Tests.
 *
 * @author Ronald Brill
 */
@RunWith(BrowserRunner.class)
public abstract class JQueryTestBase extends WebDriverTestCase {

    private static Method MethodGetWebClient_;

    static {
        try {
            MethodGetWebClient_ = HtmlUnitDriver.class.getDeclaredMethod("getWebClient");
            MethodGetWebClient_.setAccessible(true);
        }
        catch (final Exception e) {
            e.printStackTrace();
        }
    }

    private static final class OnlyLocalConnectionWrapper extends WebConnectionWrapper {
        private static final WebResponseData responseData =
                new WebResponseData("not found".getBytes(StandardCharsets.US_ASCII), 404, "Not Found",
                        new ArrayList<NameValuePair>());


        private OnlyLocalConnectionWrapper(final WebClient webClient) {
            super(webClient);
        }

        @Override
        public WebResponse getResponse(final WebRequest request) throws IOException {
            final String url = request.getUrl().toExternalForm();

            if (url.contains("localhost")) {
                return super.getResponse(request);
            }


            return new WebResponse(responseData, request, 0);
        }
    }

    private static Server SERVER_;

    /**
     * Returns the jQuery version being tested.
     * @return the jQuery version being tested
     */
    abstract String getVersion();

    /**
     * Runs the specified test.
     * @param testName the test name
     * @throws Exception if an error occurs
     */
    protected void runTest(final String testName) throws Exception {
        final String testNumber = readTestNumber(testName);
        if (testNumber == null) {
            assertEquals("Test number not found for: " + testName, NO_ALERTS_DEFINED, getExpectedAlerts());
            return;
        }
        final long runTime = 60 * DEFAULT_WAIT_TIME;
        final long endTime = System.currentTimeMillis() + runTime;

        try {
            final WebDriver webDriver = getWebDriver();

            if (webDriver instanceof HtmlUnitDriver) {
                final WebClient webClient = (WebClient) MethodGetWebClient_.invoke(webDriver);

                new OnlyLocalConnectionWrapper(webClient);
            }


            final String url = buildUrl(testNumber);
            webDriver.get(url);

            while (!getResultElementText(webDriver).startsWith("Tests completed")) {
                Thread.sleep(42);

                if (System.currentTimeMillis() > endTime) {
                    fail("Test #" + testNumber + " runs too long (longer than " + runTime / 1000 + "s)");
                }
            }

            final String result = getResultDetailElementText(webDriver, testNumber);
            final String expected = testName + " (" + getExpectedAlerts()[0] + ")";
            if (!expected.contains(result)) {
                System.out.println("--------------------------------------------");
                System.out.println("URL: " + url);
                System.out.println("--------------------------------------------");
                System.out.println("Test: " + webDriver.findElement(By.id("qunit-tests")).getText());
                System.out.println("--------------------------------------------");
                System.out.println("Failures:");
                final List<WebElement> failures = webDriver.findElements(By.cssSelector(".qunit-assert-list li.fail"));
                for (final WebElement webElement : failures) {
                    System.out.println("  " + webElement.getText());
                }
                System.out.println("--------------------------------------------");

                fail(new ComparisonFailure("", expected, result).getMessage());
            }
        }
        catch (final Exception e) {
            e.printStackTrace();
            Throwable t = e;
            while ((t = t.getCause()) != null) {
                t.printStackTrace();
            }
            throw e;
        }
    }

    private static String getResultElementText(final WebDriver webdriver) {
        // if the elem is not available or stale we return an empty string
        // this will force a second try
        try {
            final WebElement elem = webdriver.findElement(By.id("qunit-testresult"));
            try {
                return elem.getText();
            }
            catch (final StaleElementReferenceException e) {
                return "";
            }
        }
        catch (final NoSuchElementException e) {
            return "";
        }
    }

    /**
     * Determine test number for test name.
     * @param testName the name
     * @return the test number
     * @throws Exception in case of problems
     */
    protected String readTestNumber(final String testName) throws Exception {
        final String testResults = loadExpectation("/libraries/jQuery/"
                                        + getVersion() + "/expectations/results", ".txt");
        final String[] lines = testResults.split("\n");
        for (int i = 0; i < lines.length; i++) {
            final String line = lines[i];
            final int pos = line.indexOf(testName);
            if (pos != -1
                    && line.indexOf('(', pos + testName.length() + 3) == -1) {
                return Integer.toString(i + 1);
            }
        }

        return null;
    }

    /**
     * @param testNumber the number of the test to run
     * @return the test url
     */
    protected String buildUrl(final String testNumber) {
        return URL_FIRST + "jquery/test/index.html?dev&testNumber=" + testNumber;
    }

    /**
     * @param webdriver the web driver
     * @param testNumber the number of the test to run
     * @return the test result details
     */
    protected String getResultDetailElementText(final WebDriver webdriver, final String testNumber) {
        final WebElement output = webdriver.findElement(By.id("qunit-test-output0"));
        String result = output.getText();
        result = result.substring(0, result.indexOf("Rerun")).trim();
        return result;
    }

    /**
     * @throws Exception if an error occurs
     */
    @Before
    public void aaa_startSesrver() throws Exception {
        if (SERVER_ == null) {
            SERVER_ = WebServerTestCase.createWebServer("src/test/resources/libraries/jQuery/" + getVersion(), null);
        }
    }

    /**
     * @throws Exception if an error occurs
     */
    @AfterClass
    public static void zzz_stopServer() throws Exception {
        if (SERVER_ != null) {
            SERVER_.stop();
            SERVER_.destroy();
            SERVER_ = null;
        }
    }
}
