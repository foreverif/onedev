package io.onedev.server.plugin.imports.gitea;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import javax.ws.rs.client.Client;

import org.apache.http.client.utils.URIBuilder;
import org.joda.time.format.ISODateTimeFormat;

import com.fasterxml.jackson.databind.JsonNode;

import io.onedev.server.OneDev;
import io.onedev.server.entitymanager.MilestoneManager;
import io.onedev.server.entitymanager.ProjectManager;
import io.onedev.server.imports.ProjectImporter;
import io.onedev.server.model.Milestone;
import io.onedev.server.model.Project;
import io.onedev.server.model.User;
import io.onedev.server.storage.StorageManager;
import io.onedev.server.util.JerseyUtils;
import io.onedev.server.util.SimpleLogger;

public class GiteaProjectImporter extends ProjectImporter<ImportServer, ProjectImportSource, ProjectImportOption> {

	private static final long serialVersionUID = 1L;

	@Override
	public String getName() {
		return ImportUtils.NAME;
	}

	@Override
	public ProjectImportSource getWhat(ImportServer where, SimpleLogger logger) {
		ProjectImportSource importSource = new ProjectImportSource();
		Client client = where.newClient();
		try {
			String apiEndpoint = where.getApiEndpoint("/user/repos");
			for (JsonNode repoNode: ImportUtils.list(client, apiEndpoint, logger)) {
				String repoName = repoNode.get("name").asText();
				String ownerName = repoNode.get("owner").get("login").asText();
				ProjectMapping projectMapping = new ProjectMapping();
				projectMapping.setGiteaRepo(ownerName + "/" + repoName);
				projectMapping.setOneDevProject(ownerName + "-" + repoName);
				importSource.getProjectMappings().add(projectMapping);
			}					
		} finally {
			client.close();
		}
		return importSource;
	}

	@Override
	public ProjectImportOption getHow(ImportServer where, ProjectImportSource what, SimpleLogger logger) {
		ProjectImportOption importOption = new ProjectImportOption();
		List<String> repos = what.getProjectMappings().stream().map(it->it.getGiteaRepo()).collect(Collectors.toList());
		importOption.setIssueImportOption(ImportUtils.buildIssueImportOption(where, repos, logger));
		return importOption;
	}

	@Override
	public String doImport(ImportServer where, ProjectImportSource what, ProjectImportOption how, 
			boolean dryRun, SimpleLogger logger) {
		Collection<Long> projectIds = new ArrayList<>();
		Client client = where.newClient();
		try {
			Map<String, Optional<User>> users = new HashMap<>();
			ImportResult result = new ImportResult();
			for (ProjectMapping projectMapping: what.getProjectMappings()) {
				logger.log("Cloning code from repository " + projectMapping.getGiteaRepo() + "...");
				
				String apiEndpoint = where.getApiEndpoint("/repos/" + projectMapping.getGiteaRepo());
				JsonNode repoNode = JerseyUtils.get(client, apiEndpoint, logger);
				Project project = new Project();
				project.setName(projectMapping.getOneDevProject());
				project.setDescription(repoNode.get("description").asText(null));
				project.setIssueManagementEnabled(repoNode.get("has_issues").asBoolean());
				
				boolean isPrivate = repoNode.get("private").asBoolean();
				if (!isPrivate && how.getPublicRole() != null)
					project.setDefaultRole(how.getPublicRole());
				
				URIBuilder builder = new URIBuilder(repoNode.get("clone_url").asText());
				if (isPrivate)
					builder.setUserInfo("git", where.getAccessToken());
				
				if (!dryRun) {
					OneDev.getInstance(ProjectManager.class).clone(project, builder.build().toString());
					projectIds.add(project.getId());
				}

				if (how.getIssueImportOption() != null) {
					List<Milestone> milestones = new ArrayList<>();
					logger.log("Importing milestones from repository " + projectMapping.getGiteaRepo() + "...");
					apiEndpoint = where.getApiEndpoint("/repos/" + projectMapping.getGiteaRepo() + "/milestones?state=all");
					for (JsonNode milestoneNode: ImportUtils.list(client, apiEndpoint, logger)) {
						Milestone milestone = new Milestone();
						milestone.setName(milestoneNode.get("title").asText());
						milestone.setDescription(milestoneNode.get("description").asText(null));
						milestone.setProject(project);
						String dueDateString = milestoneNode.get("due_on").asText(null);
						if (dueDateString != null) 
							milestone.setDueDate(ISODateTimeFormat.dateTimeNoMillis().parseDateTime(dueDateString).toDate());
						if (milestoneNode.get("state").asText().equals("closed"))
							milestone.setClosed(true);
						
						milestones.add(milestone);
						project.getMilestones().add(milestone);
						
						if (!dryRun)
							OneDev.getInstance(MilestoneManager.class).save(milestone);
					}
					
					logger.log("Importing issues from repository " + projectMapping.getGiteaRepo() + "...");
					ImportResult currentResult = ImportUtils.importIssues(where, projectMapping.getGiteaRepo(), 
							project, true, how.getIssueImportOption(), users, dryRun, logger);
					result.nonExistentLogins.addAll(currentResult.nonExistentLogins);
					result.nonExistentMilestones.addAll(currentResult.nonExistentMilestones);
					result.unmappedIssueLabels.addAll(currentResult.unmappedIssueLabels);
					result.issuesImported |= currentResult.issuesImported;
				}
			}
			
			return result.toHtml("Repositories imported successfully");
		} catch (Exception e) {
			for (Long projectId: projectIds)
				OneDev.getInstance(StorageManager.class).deleteProjectDir(projectId);
			throw new RuntimeException(e);
		} finally {
			client.close();
		}		
	}

}
