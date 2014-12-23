import java.io.File;
import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.io.FileUtils;
import org.apache.http.HttpResponse;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.util.EntityUtils;
import org.apache.log4j.LogManager;
import org.apache.log4j.Logger;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.JSONValue;
import org.json.simple.parser.ParseException;

import com.deploy.tooling.SfdcConnectionProvider;
import com.sforce.soap.apex.CodeCoverageResult;
import com.sforce.soap.apex.CodeLocation;
import com.sforce.soap.apex.RunTestFailure;
import com.sforce.soap.apex.RunTestsRequest;
import com.sforce.soap.apex.RunTestsResult;
import com.sforce.soap.apex.SoapConnection;
import com.sforce.ws.ConnectionException;

public class TestSymbolTable {
	static final Logger logger = LogManager.getLogger(TestSymbolTable.class.getName());

	private static SfdcConnectionProvider sfdcProvider = null;

	public static void main(String[] args) {
		System.out.println("Start");
		try {
			sfdcProvider = new SfdcConnectionProvider(URL, version, username, password);
		} catch (IOException | ConnectionException e) {
			e.printStackTrace();
		}
		TestSymbolTable tst = new TestSymbolTable();
		tst.testSymbolTable();
		System.out.println("End");
	}

	@SuppressWarnings("unchecked")
	private void testSymbolTable() {
		try {
			JSONObject response = sfdcProvider.get("query/?q=select+Id,Name+from+MetadataContainer+where+Name='Compile'");
			if (Integer.valueOf(response.get("size").toString()) > 0) {
				JSONArray records = (JSONArray) response.get("records");
				for (int i = 0; i < records.size(); i++) {
					sfdcProvider.delete("sobjects/MetadataContainer/" + ((JSONObject) records.get(i)).get("Id").toString());
				}
			}

			JSONObject metaDataContainerRequest = new JSONObject();
			metaDataContainerRequest.put("Name", "Compile");
			JSONObject metaDataContainerResponse = sfdcProvider.post("sobjects/MetadataContainer", metaDataContainerRequest);
			String metaDataContainerId = (String) metaDataContainerResponse.get("id");

			JSONArray classMembers = new JSONArray();

			String query = "Select+Id,+Name,+body+from+ApexClass+where+Name++in+('MyPageController')";
			String getPath = "query/?q=" + query;
			JSONObject toolingAPIResponse = sfdcProvider.get(getPath);
			if (Integer.valueOf(toolingAPIResponse.get("size").toString()) > 0) {
				JSONArray recordObject = (JSONArray) toolingAPIResponse.get("records");
				for (int i = 0; i < recordObject.size(); ++i) {
					JSONObject rec = (JSONObject) recordObject.get(i);
					JSONObject apexClassMemberRequest = new JSONObject();
					apexClassMemberRequest.put("MetadataContainerId", metaDataContainerId);
					apexClassMemberRequest.put("ContentEntityId", rec.get("Id").toString());
					apexClassMemberRequest.put("Body", rec.get("Body").toString());
					classMembers.add(apexClassMemberRequest);
				}
			}
			if (classMembers.size() > 1) {
				JSONArray classMembersResponse = sfdcProvider.post("sobjects/ApexClassMember", classMembers);
			} else {
				JSONObject classMembersResponse = sfdcProvider.post("sobjects/ApexClassMember", (JSONObject) classMembers.get(0));
			}
			JSONObject containerAsyncRequest = new JSONObject();
			containerAsyncRequest.put("IsCheckOnly", "false");
			containerAsyncRequest.put("MetadataContainerId", metaDataContainerId);
			JSONObject containerAsyncResponse = sfdcProvider.post("sobjects/ContainerAsyncRequest", containerAsyncRequest);

			if (containerAsyncResponse.get("id") != null) {
				String asyncQuery = "sobjects/ContainerAsyncRequest/" + containerAsyncResponse.get("id");
				JSONObject containerAsyncQuery = sfdcProvider.get(asyncQuery);
				while (containerAsyncQuery.get("State").toString().equalsIgnoreCase("queued")) {
					Thread.sleep(2000);
					containerAsyncQuery = sfdcProvider.get(asyncQuery);
				}

				if (containerAsyncQuery.get("State").toString().equals("Completed")) {
					getComponents();
				}
			}
		} catch (Exception e) {
			e.printStackTrace();
			logger.error(e);
		}
	}

	private void getComponents() {
		String query = "SELECT+id,name,LengthWithoutComments,NamespacePrefix,Status,SymbolTable+from+ApexClass+where+name+in+('MyPageController')";

		try {
			JSONObject componentResponse = sfdcProvider.query(query);
			logger.debug(componentResponse);
		} catch (Exception e) {
			e.printStackTrace();
		}
	}
}
