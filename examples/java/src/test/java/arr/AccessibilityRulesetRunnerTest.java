//////////////////////////////////////////////////////////////////////////
// Copyright 2017-2019 eBay Inc.
// Author/Developer(s): Scott Izu, Aravind Kannan
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//    https://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.
//////////////////////////////////////////////////////////////////////////

package arr;

import java.util.Arrays;

import org.json.JSONArray;
import org.json.JSONObject;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.WebDriver;
import org.testng.annotations.Test;

import util.ARRProperties;
import util.WebDriverHolder;

/**
 * People can test their URLs against accessibility rules, by running this test.
 * 1. With CommandLine: mvn test -e
 * 2. With CommandLine parameters:
 *    mvn test -e -DURLS_TO_TEST="[GoogleTest] http://www.google.com"
 * 3. With TestNGEclipsePlugin: Right Click->Run As->TestNG Test
 * 4. With TestNGEclipsePlugin parameters: Right Click->Run As->Run Configurations
 *    Right Click TestNG->New
 *    Name: MyTestNGTest
 *    Class: arr.AccessibilityRulesetRunner
 *    Arguments Tab->VM arguments:
 *    -DURLS_TO_TEST="[GoogleTest] http://www.google.com"
 * 
 * Note: Class name must end with Test suffix to be run by mvn test from the command line
 */
public class AccessibilityRulesetRunnerTest {	
	@Test(dataProvider = "urlsToTest", dataProviderClass = UrlProvider.class)
	public void accessibilityRulesetRunnerTest(String url,
			String viewName) throws Exception {

		/** Should trigger an error when running in Jenkins
		 * If catch Exception, will trigger an error in report validation, stack trace will print
		 * If don't catch Exception, will trigger an error from this run, stack trace won't print
		 */
		try { // Should trigger an error when running in Jenkins, will fail report validation			
			WebDriver driver = WebDriverHolder.startupDriver();

			if(driver != null) { // Allow for driver to be null.  We require each view to have a test result.  This way, if one view fails to be tested for any reason, we can not upload the WAE report.
				driver.get(url);
			} else {
				System.out.println("!!!!! WARNING Driver was null at beginning of test");
			}
			
			System.out.println("PRE PROCESSING STAGE: Page Configuration Setup");
			ARRProperties.pageConfigurationsSetup(driver, viewName);
			
			System.out.println("PROCESSING STAGE: Running rules");
			String XPATH_ROOT = null;
			if(!ARRProperties.XPATH_ROOT.getPropertyValueBasedOnPage(driver).isEmpty()) {
				XPATH_ROOT = ARRProperties.XPATH_ROOT.getPropertyValueBasedOnPage(driver);
			}
			
			// Run Custom Ruleset
			JSONArray customRulesToRun = new JSONArray();
			for (CustomRulesetRules rule : CustomRulesetRules.values()) {
				customRulesToRun.put(rule.getLongName());
			}
			JSONObject jsonParameters = new JSONObject();
			jsonParameters.put("rulesToRun", customRulesToRun);
			jsonParameters.put("XPATH_ROOT", XPATH_ROOT);

			String customRuleset = RulesetDownloader.getCustomRulesetJS();
			String customResponse = (String) ((JavascriptExecutor) driver)
					.executeScript(customRuleset
							+ "return JSON.stringify(axs.Audit.run(" + jsonParameters.toString() + "));");

			System.out.println("ValidationRules: customResponse:"
					+ customResponse);
			
			// Run aXe Ruleset
			String aXeRulesToRun = "['area-alt','accesskeys','aria-allowed-attr','aria-required-attr','aria-required-children','aria-required-parent','aria-roles','aria-valid-attr-value','aria-valid-attr','audio-caption','blink','button-name','bypass','checkboxgroup','color-contrast','document-title','duplicate-id','empty-heading','heading-order','href-no-hash','html-lang-valid','image-redundant-alt','input-image-alt','label','layout-table','link-name','marquee','meta-refresh','meta-viewport','meta-viewport-large','object-alt','radiogroup','scopr-attr-valid','server-side-image-map','tabindex','table-duplicate-name','td-headers-attr','th-has-data-cells','valid-lang','video-caption','video-description']";

			String aXeRuleset = RulesetDownloader.getAXERulesetJS();
			
			Object aXeResponse = ((JavascriptExecutor) driver)
					.executeAsyncScript(aXeRuleset
							+ " axe.a11yCheck(document, {runOnly: {type: 'rule', values: "+aXeRulesToRun+"}}, arguments[arguments.length - 1]);");

			System.out.println("ValidationRules: aXeResponse:"
					+ aXeResponse);
			
			WebDriverHolder.shutdownDriver();
			
		} catch (Exception ex) { // This should never be hit
			ex.printStackTrace();
			throw ex; // To make Jenkins job fail
		}
	}
}
