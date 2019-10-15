//////////////////////////////////////////////////////////////////////////
// Copyright 2017-2019 eBay Inc.
// Author/Developer(s): Aravind Kannan, Scott Izu
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

import java.io.File;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import org.json.JSONArray;
import org.json.JSONObject;
import org.openqa.selenium.By;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.testng.annotations.Test;

import util.ARRProperties;
import util.JSONToHTMLConverter;
import util.ScreenshotsProcessor;
import util.ScreenshotsProcessor.ScreenShotElementRectangle;
import util.UrlProvider;
import util.WebDriverHolder;

/**
 * People can test their URLs against accessibility rules, by running this test.
 * 1. With CommandLine: mvn test -e
 * 2. With CommandLine parameters:
 *    mvn test -e -DURLS_TO_TEST="[GoogleTest] http://www.google.com"
 * 3. With TestNGEclipsePlugin: Right Click->Run As->TestNG Test
 * 4. With TestNGEclipsePlugin parameters: Right Click->Run As->Run Configurations
 *    Right Click TestNG->New
 *    Name: AccessibilityRulesetRunnerTest
 *    Class: arr.AccessibilityRulesetRunner
 *    Arguments Tab->VM arguments:
 *    -DURLS_TO_TEST="[GoogleTest] http://www.google.com"
 * 
 * Note: Class name must end with Test suffix to be run by mvn test from the command line
 */
public class AccessibilityRulesetRunnerTest {	
	@Test(dataProvider = "urlsToTest", dataProviderClass = UrlProvider.class)
	public void accessibilityRulesetRunnerTest(String url, String viewName) throws Exception {

		/** Should trigger an error when running in Jenkins
		 * If catch Exception, will trigger an error in report validation, stack trace will print
		 * If don't catch Exception, will trigger an error from this run, stack trace won't print
		 */
		try { // Should trigger an error when running in Jenkins, will fail report validation			
			WebDriver driver = WebDriverHolder.startupDriver();

			if(driver != null) { // Allow for driver to be null.  We require each view to have a test result.  This way, if one view fails to be tested for any reason, we can not upload the ARR report.
				driver.get(url);
			} else {
				System.out.println("!!!!! WARNING Driver was null at beginning of test");
			}
			
			System.out.println("PRE PROCESSING STAGE: Page Configuration Setup");
			ARRProperties.viewConfigurationsSetup(driver, viewName);
			
			System.out.println("PRE PROCESSING STAGE: Setup Results");
			JSONObject results = new JSONObject();
			results.put("viewName", viewName);
			results.put("url", url);
			results.put("reportTitle", ARRProperties.REPORT_TITLE.getPropertyValue());
			results.put("xpathRoot", ARRProperties.XPATH_ROOT.getPropertyValue());

			System.out.println("PREPROCESSING STAGE: Take View Screenshots");
			ScreenshotsProcessor sp = new ScreenshotsProcessor();
			takeViewScreenshots(driver, results, sp);

			System.out.println("PROCESSING STAGE: Running Custom Ruleset");
			runCustomRuleset(driver, results);

			System.out.println("PROCESSING STAGE: Running aXe Ruleset");
			runAXERuleset(driver, results);

			System.out.println("POSTPROCESSING STAGE: Take View Screenshots");

			WebDriverHolder.shutdownDriver();
			
			// Convert JSON to HTML
			System.out.println("POST PROCESSING STAGE: Creating HTML Report");
			new JSONToHTMLConverter().convert(results);

			System.out.println("Results:"+results);
			
		} catch (Exception ex) { // This should never be hit
			ex.printStackTrace();
			throw ex; // To make Jenkins job fail
		}
	}
	
	private void takeViewScreenshots(WebDriver driver, JSONObject results, ScreenshotsProcessor sp)  throws Exception {
		String xpathRoot = results.getString("xpathRoot");
		
		if (!xpathRoot.isEmpty()) {
			System.out.println("PRE PROCESSING STAGE: Page Screenshot Scroll to Root...");
			WebElement rootElement = driver.findElements(By.xpath(xpathRoot)).get(0);
			WebElement htmlElement = driver.findElements(By.xpath("//html")).get(0);
			((JavascriptExecutor) driver).executeScript("arguments[0].scrollIntoView(true);", rootElement);
			Thread.sleep(3000);
			((JavascriptExecutor) driver).executeScript("arguments[0].scrollIntoView(true);", htmlElement);
		}
		
		String viewName = results.getString("viewName");

		new File("output").mkdirs();

		String imageName1 = viewName.replaceAll(" ", "_") + "_SCREEN.jpg";
		
		sp.createSnapshotForScreen(driver, "output/" + imageName1);
		results.put("viewImage", imageName1); // URLS on different threads were sharing screenshots, so have it in results
		if (!xpathRoot.isEmpty()) {
			WebElement rootElement = driver.findElements(By.xpath(xpathRoot)).get(0);
			ScreenShotElementRectangle sser = new ScreenShotElementRectangle(rootElement.getLocation(), rootElement.getSize());

			String imageName = viewName.replaceAll(" ", "_")+ "_ROOT_.jpg";
			
			sp.createSnapshot(sser, "output/" + imageName);
			results.put("xpathImage", imageName); // URLS on different threads were sharing screenshots, so have it in results
			System.out.println("RootElementImage: " + imageName);
		}
	}

	private void runCustomRuleset(WebDriver driver, JSONObject results) {
		String xpathRoot = results.getString("xpathRoot");
		
		// Run Custom Ruleset
		JSONArray customRulesToRun = new JSONArray();
		for (CustomRulesetRules rule : CustomRulesetRules.values()) {
			customRulesToRun.put(rule.getLongName());
		}
		JSONObject jsonParameters = new JSONObject();
		jsonParameters.put("rulesToRun", customRulesToRun);
		if (!xpathRoot.isEmpty()) {
			jsonParameters.put("XPATH_ROOT", xpathRoot);
		}

		String customRuleset = RulesetDownloader.getCustomRulesetJS();
		String customResponse = (String) ((JavascriptExecutor) driver)
				.executeScript(customRuleset
						+ "return JSON.stringify(axs.Audit.run(" + jsonParameters.toString() + "));");
		results.put("custom", new JSONArray(customResponse)); // addCustomResponseToResult
//		System.out.println("ValidationRules: customResponse:" + customResponse);
	}

	private void runAXERuleset(WebDriver driver, JSONObject results) {
		// Run aXe Ruleset
		String aXeRulesToRun = "['area-alt','accesskeys','aria-allowed-attr','aria-required-attr','aria-required-children','aria-required-parent','aria-roles','aria-valid-attr-value','aria-valid-attr','audio-caption','blink','button-name','bypass','checkboxgroup','color-contrast','document-title','duplicate-id','empty-heading','heading-order','href-no-hash','html-lang-valid','image-redundant-alt','input-image-alt','label','layout-table','link-name','marquee','meta-refresh','meta-viewport','meta-viewport-large','object-alt','radiogroup','scopr-attr-valid','server-side-image-map','tabindex','table-duplicate-name','td-headers-attr','th-has-data-cells','valid-lang','video-caption','video-description']";

		String aXeRuleset = RulesetDownloader.getAXERulesetJS();
		
		Map aXeResponse = (Map) ((JavascriptExecutor) driver)
				.executeAsyncScript(aXeRuleset
						+ " axe.a11yCheck(document, {runOnly: {type: 'rule', values: "+aXeRulesToRun+"}}, arguments[arguments.length - 1]);");

		addAXEResponseToResult(results, aXeResponse, aXeRulesToRun);
//		System.out.println("ValidationRules: aXeResponse:" + aXeResponse);
	}

	private void addAXEResponseToResult(JSONObject results, Map aXeResponse, String aXeRulesToRun) {
		// Create single results object
		results.put("axe", new JSONArray());
		JSONObject axeresults = new JSONObject(aXeResponse);
//		System.out.println("axeresults:"+axeresults);
		
		// Put failed rules into the single results object
		Set<String> rulesWithViolation = new HashSet<String>();
		if(axeresults.has("violations")) {
			JSONArray violations = axeresults.getJSONArray("violations");
			for(int i=0; i<violations.length(); i++) {
				JSONObject axerule = new JSONObject();
				axerule.put("ruleName", violations.getJSONObject(i).get("id"));
				axerule.put("violations", new JSONArray());
				axerule.getJSONArray("violations").put(violations.getJSONObject(i));
				results.getJSONArray("axe").put(axerule);
				rulesWithViolation.add(axerule.getString("ruleName"));
			}
		}
		
		// Put passed rules into the single results object
		JSONArray axeRulesToRun = new JSONArray(aXeRulesToRun);
		for(int i=0; i<axeRulesToRun.length(); i++) { // Show that the test was run
			if(!rulesWithViolation.contains(axeRulesToRun.getString(i))) {
				JSONObject axerule = new JSONObject();
				axerule.put("ruleName", axeRulesToRun.getString(i));
				axerule.put("violations", new JSONArray());
				results.getJSONArray("axe").put(axerule);
			}
		}
	}
}
