

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;

import java.io.PrintWriter;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.List;

import org.apache.commons.io.IOUtils;
import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;

import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPut;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.BasicResponseHandler;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.util.EntityUtils;

import com.thed.zephyr.cloud.rest.ZFJCloudRestClient;
import com.thed.zephyr.cloud.rest.client.JwtGenerator;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

/**
 * @author swapna.vemula 12-Dec-2016
 *
 */
public class sampleJwtGenerator {

	public static String jsonPath;
	
	/**
	 * @param args
	 * @author Created by swapna.vemula on 12-Dec-2016.
	 * @throws URISyntaxException
	 * @throws JobProgressException
	 * @throws JSONException
	 * @throws IOException
	 * @throws IllegalStateException
	 */


	private static String generateJWT(String zephyrBaseUrl , String accessKey, String secretKey, String accountId, String path, String action) throws Exception {
		
		ZFJCloudRestClient client = ZFJCloudRestClient.restBuilder(zephyrBaseUrl, accessKey, secretKey, accountId)
				.build();
		
		JwtGenerator jwtGenerator = client.getJwtGenerator();
		URI uri = new URI(path);
		int expirationInSec = 600;
		String jwt = jwtGenerator.generateJWT(action, uri, expirationInSec);
		return jwt;
	}

	private static String getStatus(String status) {
		String statusId = "";

		switch (status) {
		case "PASS":
			statusId = "1";
			break;
			
		case "FAIL":
			statusId = "2";
			break;
			
		case "WIP":
			statusId = "3";
			break;
			
		case "BLOCKED":
			statusId = "4";
			break;
			
		case "UNEXECUTED":
			statusId = "-1";
			break;
			
			default:
				statusId = "-1";
				break;
		}

		return statusId;
	}

	public static void main(String[] args) throws Exception {

		int length = args.length;
		String stmPath = "";
		String jsonFile = "";
		int notExecuted = 0;
		List<String> updated = new ArrayList<String>(); 
		List<String> notUpd = new ArrayList<String>();;
		
		if (length == 0) {
			stmPath = "C:\\Temp\\";
			jsonFile = "ZephyrUpdate.txt";
		} else {
			if (length == 1) {
				if (args[0].endsWith(".txt")) {
					jsonFile = args[0];
					stmPath = "C:\\Temp\\";
				} else {
					stmPath = args[0];
					jsonFile = "ZephyrUpdate.txt";
				}
			}
		}
		PrintWriter writer = new PrintWriter(stmPath+"\\JavaCABuild\\Zephyr_Log.txt", "UTF-8");
		
		jsonPath = stmPath + "\\JavaCABuild\\Execution\\" + jsonFile;

		if (jsonPath.contains("%programfiles(x86)%")) {
			jsonPath = jsonPath.replace("%programfiles(x86)%", "C:\\Program Files (x86)");
		}

		InputStream is = new FileInputStream(jsonPath);
		String jsonTxt = IOUtils.toString(is);
		System.out.println(jsonTxt);
		JSONObject json = new JSONObject(jsonTxt);
		is.close();

		try {
			String zephyrBaseUrl = "https://prod-api.zephyr4jiracloud.com/connect";
			String accessKey = json.getString("accessKey"); 
			String secretKey = json.getString("secretKey");
			String accountId = json.getString("accountId");
			String projectId = json.getString("projectId");
			String cycle = json.getString("cycle");
			String testSet = json.getString("testSet");
			JSONArray instances = json.getJSONArray("instances");

			ZFJCloudRestClient client = ZFJCloudRestClient.restBuilder(zephyrBaseUrl, accessKey, secretKey, accountId)
					.build();
			JwtGenerator jwtGenerator = client.getJwtGenerator();

			String createCycleUri = zephyrBaseUrl + "/public/rest/api/1.0/cycles/search?versionId=-1&projectId="
					+ projectId;

			URI uri = new URI(createCycleUri);
			int expirationInSec = 600;
			String jwt = jwtGenerator.generateJWT("GET", uri, expirationInSec);

			System.out.println(uri.toString());
			System.out.println(jwt);

			HttpClient zephyr = new DefaultHttpClient();
			HttpGet request = new HttpGet(uri.toString());
			request.addHeader("Authorization", jwt);
			request.addHeader("zapiAccessKey", accessKey);

			HttpResponse response = zephyr.execute(request);
			BasicResponseHandler hand = new BasicResponseHandler();
			String cycleString = hand.handleResponse(response);
			JSONArray cycles = new JSONArray(cycleString);
			System.out.println(cycles);

			String cycleId = "";
			int versionId = -1;
			for (int i = 0; i < cycles.length(); i++) {
				JSONObject obj = cycles.getJSONObject(i);
				if (obj.getString("name").equals(cycle)) {
					cycleId = obj.getString("id");
					versionId = obj.getInt("versionId");
					break;
				}
			}

			if (cycleId != "") {
				
				String getFolders = zephyrBaseUrl + "/public/rest/api/1.0/folders?versionId="+ versionId +"&cycleId="+ cycleId +"&projectId="+ projectId;
				String folderTkn = generateJWT(zephyrBaseUrl,accessKey,secretKey,accountId,getFolders,"GET");
				
				request = new HttpGet(getFolders);
				request.addHeader("Authorization", folderTkn);
				request.addHeader("zapiAccessKey", accessKey);

				response = zephyr.execute(request);
				hand = new BasicResponseHandler();
				String respFolder = hand.handleResponse(response);
				JSONArray folders = new JSONArray(respFolder);
				String folderId = "";
				for(int i=0; i<folders.length(); i++) {
					JSONObject folder = folders.getJSONObject(i);
					
					if(folder.getString("name").equals(testSet) && folder.getString("cycleId").equals(cycleId)) {
						folderId = folder.getString("id");
					}
				}
				
				
				if(folderId != "") {
					String folderExe = zephyrBaseUrl + "/public/rest/api/1.0/executions/search/folder/"+folderId+"?versionId="+versionId+"&cycleId="+cycleId+"&projectId="+projectId;
					//URI uriCycle = new URI(getCycle);
					String jwt1 = generateJWT(zephyrBaseUrl,accessKey,secretKey,accountId,folderExe,"GET");
					
					request = new HttpGet(folderExe);
					request.addHeader("Authorization", jwt1);
					request.addHeader("zapiAccessKey", accessKey);

					response = zephyr.execute(request);
					hand = new BasicResponseHandler();
					cycleString = hand.handleResponse(response);
					cycleString = cycleString.substring(1, cycleString.length()-1);
					cycleString = "{" + cycleString + "}";
					JSONObject cycle2 = new JSONObject(cycleString);
					System.out.println(cycle);

					JSONArray searchObjectList = cycle2.getJSONArray("searchObjectList");
					
					for (int x = 0; x < instances.length(); x++) {
						JSONObject it = instances.getJSONObject(x);

						for (int i = 0; i < searchObjectList.length(); i++) {

							JSONObject test = searchObjectList.getJSONObject(i);
							String testZephyrName = test.getString("issueSummary");
							String instanceName = it.getString("InstanceName");
							if (test.getString("issueSummary").equals(it.getString("InstanceName"))) {

								JSONObject execution = test.getJSONObject("execution");
								String executionId = execution.getString("id");
								int issueId = execution.getInt("issueId");
								String newStatus =  getStatus(it.getString("Status"));

								String update = zephyrBaseUrl + "/public/rest/api/1.0/execution/" + executionId;

								String updateToken = generateJWT(zephyrBaseUrl,accessKey,secretKey,accountId,update,"PUT");
								HttpPut upd = new HttpPut(update);
								upd.addHeader("Authorization", updateToken);
								upd.addHeader("zapiAccessKey", accessKey);

								String requestJson = "{\"cycleId\":\"" + cycleId + "\",\"issueId\":" + issueId
										+ " ,\"projectId\":" + projectId + ",\"status\":{\"id\":\"" + newStatus + "\"},\"versionId\":"
										+ versionId + "}";
								requestJson = requestJson.trim();
								StringEntity entity = new StringEntity(requestJson, "UTF-8");
								entity.setContentType("application/json");
								upd.setEntity(entity);

								response = zephyr.execute(upd);
								
								
								
								EntityUtils.consume(response.getEntity());
								System.out.println("TEST CASE "+ it.getString("InstanceName") +" UPDATED");
								writer.println("TEST CASE "+ it.getString("InstanceName") +" UPDATED");
								writer.println(" ");
								updated.add(it.getString("InstanceName"));
							}else {
								notExecuted++;
								if( !(notUpd.contains(it.getString("InstanceName")) )) 
									notUpd.add(it.getString("InstanceName"));
							}

						}

					}
				}else {
					writer.println("Folder " + testSet + " not found in Zephyr");
					writer.println("Failed");
				}
				
			}else {
				writer.println("Cycle " + cycle + " not found in Zephyr");
				writer.println("Failed");
			}
			
			if(notExecuted > 0) {
				
				if(notUpd.removeAll(updated))
					if( !(notUpd.isEmpty()) ) {
						writer.println("Test cases not found were not updated:");
						for (String testCase : notUpd) {
							writer.println(testCase);
						}
						writer.println("Failed");
					}
				
			}
				
			writer.close();
		} catch (Exception ex) {
			writer.println(ex.getMessage());
			writer.println("Failed");
			writer.close();
		}

	}

}