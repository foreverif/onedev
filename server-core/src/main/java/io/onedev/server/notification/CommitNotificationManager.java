package io.onedev.server.notification;

import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;

import javax.annotation.Nullable;
import javax.inject.Inject;
import javax.inject.Singleton;

import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.revwalk.RevCommit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.unbescape.html.HtmlEscape;

import com.google.common.collect.Lists;

import io.onedev.commons.launcher.loader.Listen;
import io.onedev.server.entitymanager.SettingManager;
import io.onedev.server.entitymanager.UrlManager;
import io.onedev.server.event.RefUpdated;
import io.onedev.server.git.GitUtils;
import io.onedev.server.markdown.MarkdownManager;
import io.onedev.server.model.CommitQuerySetting;
import io.onedev.server.model.Project;
import io.onedev.server.model.User;
import io.onedev.server.model.support.NamedQuery;
import io.onedev.server.persistence.annotation.Sessional;
import io.onedev.server.search.commit.CommitQuery;

@Singleton
public class CommitNotificationManager extends AbstractNotificationManager {
	
	private static final Logger logger = LoggerFactory.getLogger(CommitNotificationManager.class);
	
	private final MailManager mailManager;
	
	private final UrlManager urlManager;
	
	@Inject
	public CommitNotificationManager(MarkdownManager markdownManager, MailManager mailManager, UrlManager urlManager, 
			SettingManager settingManager) {
		super(markdownManager, settingManager);
		this.mailManager = mailManager;
		this.urlManager = urlManager;
	}

	private void fillSubscribedQueryStrings(Map<User, Collection<String>> subscribedQueryStrings, 
			User user, @Nullable NamedQuery query) {
		if (query != null) {
			Collection<String> value = subscribedQueryStrings.get(user);
			if (value == null) {
				value = new HashSet<>();
				subscribedQueryStrings.put(user, value);
			}
			value.add(query.getQuery());
		}
	}
	
	@Sessional
	@Listen
	public void on(RefUpdated event) {
		if (!event.getNewCommitId().equals(ObjectId.zeroId())) {
			Project project = event.getProject();
			Map<User, Collection<String>> subscribedQueryStrings = new HashMap<>();
			for (CommitQuerySetting setting: project.getUserCommitQuerySettings()) {
				for (String name: setting.getQuerySubscriptionSupport().getQuerySubscriptions()) {
					fillSubscribedQueryStrings(subscribedQueryStrings, setting.getUser(), 
							NamedQuery.find(project.getNamedCommitQueries(), name));
				}
				for (String name: setting.getQuerySubscriptionSupport().getUserQuerySubscriptions()) { 
					fillSubscribedQueryStrings(subscribedQueryStrings, setting.getUser(), 
							NamedQuery.find(setting.getUserQueries(), name));
				}
			}
			
			Collection<String> notifyEmails = new HashSet<>();
			for (Map.Entry<User, Collection<String>> entry: subscribedQueryStrings.entrySet()) {
				User user = entry.getKey();
				for (String queryString: entry.getValue()) {
					User.push(user);
					try {
						if (CommitQuery.parse(project, queryString).matches(event)) {
							notifyEmails.add(user.getEmail());
							break;
						}
					} catch (Exception e) {
						String message = String.format("Error processing commit subscription "
								+ "(user: %s, project: %s, commit: %s, query: %s)", 
								user.getName(), project.getName(), event.getNewCommitId().name(), queryString);
						logger.error(message, e);
					} finally {
						User.pop();
					}
				}
			}
			
			RevCommit commit = project.getRevCommit(event.getNewCommitId(), false);
			if (commit != null) {
				String target = GitUtils.ref2branch(event.getRefName());
				if (target == null) {
					target = GitUtils.ref2tag(event.getRefName());
					if (target == null) 
						target = event.getRefName();
				}
				
				String subject = String.format("[Commit %s:%s] (%s) %s", 
						project.getName(), GitUtils.abbreviateSHA(commit.name()), target, commit.getShortMessage());

				String url = urlManager.urlFor(project, commit);
				String summary = String.format("Authored by %s", commit.getAuthorIdent().getName());

				String textMessage = GitUtils.getDetailMessage(commit);
				String htmlMessage = null;
				if (textMessage != null) 
					htmlMessage = "<pre>" + HtmlEscape.escapeHtml5(textMessage) + "</pre>";
				
				String threadingReferences = "<commit-" + commit.name() + "@onedev>";
				
				mailManager.sendMailAsync(Lists.newArrayList(), Lists.newArrayList(), notifyEmails, subject, 
						getHtmlBody(event, summary, htmlMessage, url, false, null), 
						getTextBody(event, summary, textMessage, url, false, null), 
						null, threadingReferences);
			}
		}
	}
}
