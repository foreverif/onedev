package io.onedev.server.plugin.imports.gitea;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import javax.ws.rs.client.Client;

import org.hibernate.validator.constraints.NotEmpty;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.databind.JsonNode;

import io.onedev.server.buildspec.job.log.StyleBuilder;
import io.onedev.server.util.SimpleLogger;
import io.onedev.server.web.editable.annotation.ChoiceProvider;
import io.onedev.server.web.editable.annotation.Editable;
import io.onedev.server.web.util.WicketUtils;

@Editable
public class IssueImportSource implements Serializable {

	private static final long serialVersionUID = 1L;
	
	private static final Logger logger = LoggerFactory.getLogger(IssueImportSource.class);
	
	private String repository;

	@Editable(order=400, name="Gitea Repository", description="Choose Gitea repository to import issues from")
	@ChoiceProvider("getRepositoryChoices")
	@NotEmpty
	public String getRepository() {
		return repository;
	}

	public void setRepository(String repository) {
		this.repository = repository;
	}
	
	@SuppressWarnings("unused")
	private static List<String> getRepositoryChoices() {
		List<String> choices = new ArrayList<>();
		
		ImportServer server = WicketUtils.getPage().getMetaData(ImportServer.META_DATA_KEY);
		
		Client client = server.newClient();
		try {
			String apiEndpoint = server.getApiEndpoint("/user/repos");
			for (JsonNode repoNode: ImportUtils.list(client, apiEndpoint, new SimpleLogger() {

				@Override
				public void log(String message, StyleBuilder styleBuilder) {
					logger.info(message);
				}
				
			})) {
				String repoName = repoNode.get("name").asText();
				String ownerName = repoNode.get("owner").get("login").asText();
				choices.add(ownerName + "/" + repoName);
			}
		} finally {
			client.close();
		}
		
		Collections.sort(choices);
		return choices;
	}
	
}
