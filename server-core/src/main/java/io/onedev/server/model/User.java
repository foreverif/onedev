package io.onedev.server.model;

import static io.onedev.server.model.User.PROP_ACCESS_TOKEN;
import static io.onedev.server.model.User.PROP_EMAIL;
import static io.onedev.server.model.User.PROP_FULL_NAME;
import static io.onedev.server.model.User.PROP_NAME;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Stack;
import java.util.stream.Collectors;

import javax.annotation.Nullable;
import javax.persistence.CascadeType;
import javax.persistence.Column;
import javax.persistence.Embedded;
import javax.persistence.Entity;
import javax.persistence.Index;
import javax.persistence.Lob;
import javax.persistence.OneToMany;
import javax.persistence.Table;
import javax.persistence.UniqueConstraint;

import org.apache.commons.lang3.RandomStringUtils;
import org.apache.shiro.authc.AuthenticationInfo;
import org.apache.shiro.subject.PrincipalCollection;
import org.apache.shiro.subject.SimplePrincipalCollection;
import org.apache.shiro.subject.Subject;
import org.eclipse.jgit.lib.PersonIdent;
import org.hibernate.annotations.Cache;
import org.hibernate.annotations.CacheConcurrencyStrategy;
import org.hibernate.validator.constraints.Email;
import org.hibernate.validator.constraints.NotEmpty;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.google.common.base.MoreObjects;
import com.google.common.collect.Sets;

import io.onedev.server.OneDev;
import io.onedev.server.entitymanager.SettingManager;
import io.onedev.server.model.support.NamedProjectQuery;
import io.onedev.server.model.support.QuerySetting;
import io.onedev.server.model.support.SsoInfo;
import io.onedev.server.model.support.administration.authenticator.Authenticator;
import io.onedev.server.model.support.administration.sso.SsoConnector;
import io.onedev.server.model.support.build.NamedBuildQuery;
import io.onedev.server.model.support.issue.NamedIssueQuery;
import io.onedev.server.model.support.pullrequest.NamedPullRequestQuery;
import io.onedev.server.security.SecurityUtils;
import io.onedev.server.util.NameAware;
import io.onedev.server.util.match.MatchScoreUtils;
import io.onedev.server.util.validation.annotation.EmailList;
import io.onedev.server.util.validation.annotation.UserName;
import io.onedev.server.util.watch.QuerySubscriptionSupport;
import io.onedev.server.util.watch.QueryWatchSupport;
import io.onedev.server.web.editable.annotation.Editable;
import io.onedev.server.web.editable.annotation.Password;

@Entity
@Table(
		indexes={@Index(columnList=PROP_NAME), @Index(columnList=PROP_EMAIL), 
				@Index(columnList=PROP_FULL_NAME), @Index(columnList=SsoInfo.COLUMN_CONNECTOR), 
				@Index(columnList=SsoInfo.COLUMN_SUBJECT), @Index(columnList=PROP_ACCESS_TOKEN)}, 
		uniqueConstraints={@UniqueConstraint(columnNames={SsoInfo.COLUMN_CONNECTOR, SsoInfo.COLUMN_SUBJECT})})
@Cache(usage=CacheConcurrencyStrategy.READ_WRITE)
@Editable
public class User extends AbstractEntity implements AuthenticationInfo, NameAware {

	private static final long serialVersionUID = 1L;
	
	public static final int ACCESS_TOKEN_LEN = 40;
	
	public static final Long UNKNOWN_ID = -2L;
	
	public static final Long SYSTEM_ID = -1L;
	
	public static final Long ROOT_ID = 1L;
	
	public static final String SYSTEM_NAME = "OneDev";
	
	public static final String UNKNOWN_NAME = "Unknown";
	
	public static final String EXTERNAL_MANAGED = "external_managed";
	
	public static final String PROP_NAME = "name";
	
	public static final String PROP_EMAIL = "email";
	
	public static final String PROP_GIT_EMAIL = "gitEmail";
	
	public static final String PROP_ALTERNATE_EMAILS = "alternateEmails";
	
	public static final String PROP_PASSWORD = "password";
	
	public static final String PROP_FULL_NAME = "fullName";
	
	public static final String PROP_SSO_INFO = "ssoInfo";
	
	public static final String PROP_ACCESS_TOKEN = "accessToken";
	
	public static final String AUTH_SOURCE_BUILTIN_USER_STORE = "Builtin User Store";
	
	public static final String AUTH_SOURCE_EXTERNAL_AUTHENTICATOR = "External Authenticator";
	
	public static final String AUTH_SOURCE_SSO_PROVIDER = "SSO Provider: ";
	
	private static ThreadLocal<Stack<User>> stack =  new ThreadLocal<Stack<User>>() {

		@Override
		protected Stack<User> initialValue() {
			return new Stack<User>();
		}
	
	};
	
	@Column(unique=true, nullable=false)
    private String name;

    @Column(length=1024, nullable=false)
    @JsonIgnore
    private String password;

	private String fullName;

	@JsonIgnore
	@Embedded
	private SsoInfo ssoInfo = new SsoInfo();
	
	@Column(unique=true, nullable=false)
	private String email;
	
	@Column
	private String gitEmail;
	
	@Lob
	@Column(nullable=false, length=1024)
	private ArrayList<String> alternateEmails = new ArrayList<>();
	
	@Column(unique=true, nullable=false)
	private String accessToken = RandomStringUtils.randomAlphanumeric(ACCESS_TOKEN_LEN);
	
	@OneToMany(mappedBy="user", cascade=CascadeType.REMOVE)
	@Cache(usage=CacheConcurrencyStrategy.READ_WRITE)
	private Collection<UserAuthorization> authorizations = new ArrayList<>();
	
	@OneToMany(mappedBy="user", cascade=CascadeType.REMOVE)
	@Cache(usage=CacheConcurrencyStrategy.READ_WRITE)
	private Collection<Membership> memberships = new ArrayList<>();
	
	@OneToMany(mappedBy="user", cascade=CascadeType.REMOVE)
	private Collection<PullRequestReview> pullRequestReviews = new ArrayList<>();
	
	@OneToMany(mappedBy="user", cascade=CascadeType.REMOVE)
	private Collection<PullRequestAssignment> pullRequestAssignments = new ArrayList<>();
	
    @OneToMany(mappedBy="user", cascade=CascadeType.REMOVE)
    private Collection<PullRequestWatch> pullRequestWatches = new ArrayList<>();

    @OneToMany(mappedBy="user", cascade=CascadeType.REMOVE)
    private Collection<IssueWatch> issueWatches = new ArrayList<>();
    
    @OneToMany(mappedBy="user", cascade=CascadeType.REMOVE)
    private Collection<IssueVote> issueVotes = new ArrayList<>();
    
    @OneToMany(mappedBy="user", cascade=CascadeType.REMOVE)
    private Collection<IssueQuerySetting> projectIssueQuerySettings = new ArrayList<>();
    
    @OneToMany(mappedBy="user", cascade=CascadeType.REMOVE)
    private Collection<BuildQuerySetting> projectBuildQuerySettings = new ArrayList<>();
    
    @OneToMany(mappedBy="user", cascade=CascadeType.REMOVE)
    private Collection<PullRequestQuerySetting> projectPullRequestQuerySettings = new ArrayList<>();
    
    @OneToMany(mappedBy="user", cascade=CascadeType.REMOVE)
    private Collection<CommitQuerySetting> projectCommitQuerySettings = new ArrayList<>();
    
    @OneToMany(mappedBy="user", cascade=CascadeType.REMOVE)
    private Collection<CodeCommentQuerySetting> projectCodeCommentQuerySettings = new ArrayList<>();
    
    @OneToMany(mappedBy="owner", cascade=CascadeType.REMOVE)
	@Cache(usage=CacheConcurrencyStrategy.READ_WRITE)
    private Collection<SshKey> sshKeys = new ArrayList<>();

    @JsonIgnore
	@Lob
	@Column(nullable=false, length=65535)
	private ArrayList<NamedProjectQuery> userProjectQueries = new ArrayList<>();
	
    @JsonIgnore
	@Lob
	@Column(nullable=false, length=65535)
	private ArrayList<NamedIssueQuery> userIssueQueries = new ArrayList<>();

    @JsonIgnore
	@Lob
	@Column(nullable=false, length=65535)
	private LinkedHashMap<String, Boolean> userIssueQueryWatches = new LinkedHashMap<>();
	
    @JsonIgnore
	@Lob
	@Column(nullable=false, length=65535)
	private LinkedHashMap<String, Boolean> issueQueryWatches = new LinkedHashMap<>();
	
    @JsonIgnore
	@Lob
	@Column(nullable=false, length=65535)
	private ArrayList<NamedPullRequestQuery> userPullRequestQueries = new ArrayList<>();

    @JsonIgnore
	@Lob
	@Column(nullable=false, length=65535)
	private LinkedHashMap<String, Boolean> userPullRequestQueryWatches = new LinkedHashMap<>();

    @JsonIgnore
	@Lob
	@Column(nullable=false, length=65535)
	private LinkedHashMap<String, Boolean> pullRequestQueryWatches = new LinkedHashMap<>();
	
    @JsonIgnore
	@Lob
	@Column(nullable=false, length=65535)
	private ArrayList<NamedBuildQuery> userBuildQueries = new ArrayList<>();

    @JsonIgnore
	@Lob
	@Column(nullable=false, length=65535)
	private LinkedHashSet<String> userBuildQuerySubscriptions = new LinkedHashSet<>();

    @JsonIgnore
	@Lob
	@Column(nullable=false, length=65535)
	private LinkedHashSet<String> buildQuerySubscriptions = new LinkedHashSet<>();
	
    private transient Collection<Group> groups;
    
	public QuerySetting<NamedProjectQuery> getProjectQuerySetting() {
		return new QuerySetting<NamedProjectQuery>() {

			@Override
			public Project getProject() {
				return null;
			}

			@Override
			public User getUser() {
				return User.this;
			}

			@Override
			public ArrayList<NamedProjectQuery> getUserQueries() {
				return userProjectQueries;
			}

			@Override
			public void setUserQueries(ArrayList<NamedProjectQuery> userQueries) {
				userProjectQueries = userQueries;
			}

			@Override
			public QueryWatchSupport<NamedProjectQuery> getQueryWatchSupport() {
				return null;
			}

			@Override
			public QuerySubscriptionSupport<NamedProjectQuery> getQuerySubscriptionSupport() {
				return null;
			}
			
		};
	}
	
	public QuerySetting<NamedIssueQuery> getIssueQuerySetting() {
		return new QuerySetting<NamedIssueQuery>() {

			@Override
			public Project getProject() {
				return null;
			}

			@Override
			public User getUser() {
				return User.this;
			}

			@Override
			public ArrayList<NamedIssueQuery> getUserQueries() {
				return userIssueQueries;
			}

			@Override
			public void setUserQueries(ArrayList<NamedIssueQuery> userQueries) {
				userIssueQueries = userQueries;
			}

			@Override
			public QueryWatchSupport<NamedIssueQuery> getQueryWatchSupport() {
				return new QueryWatchSupport<NamedIssueQuery>() {

					@Override
					public LinkedHashMap<String, Boolean> getUserQueryWatches() {
						return userIssueQueryWatches;
					}

					@Override
					public LinkedHashMap<String, Boolean> getQueryWatches() {
						return issueQueryWatches;
					}
					
				};
			}

			@Override
			public QuerySubscriptionSupport<NamedIssueQuery> getQuerySubscriptionSupport() {
				return null;
			}
			
		};
	}
	
	public QuerySetting<NamedPullRequestQuery> getPullRequestQuerySetting() {
		return new QuerySetting<NamedPullRequestQuery>() {

			@Override
			public Project getProject() {
				return null;
			}

			@Override
			public User getUser() {
				return User.this;
			}

			@Override
			public ArrayList<NamedPullRequestQuery> getUserQueries() {
				return userPullRequestQueries;
			}

			@Override
			public void setUserQueries(ArrayList<NamedPullRequestQuery> userQueries) {
				userPullRequestQueries = userQueries;
			}

			@Override
			public QueryWatchSupport<NamedPullRequestQuery> getQueryWatchSupport() {
				return new QueryWatchSupport<NamedPullRequestQuery>() {

					@Override
					public LinkedHashMap<String, Boolean> getUserQueryWatches() {
						return userPullRequestQueryWatches;
					}

					@Override
					public LinkedHashMap<String, Boolean> getQueryWatches() {
						return pullRequestQueryWatches;
					}
					
				};
			}

			@Override
			public QuerySubscriptionSupport<NamedPullRequestQuery> getQuerySubscriptionSupport() {
				return null;
			}
			
		};
	}
	
	public QuerySetting<NamedBuildQuery> getBuildQuerySetting() {
		return new QuerySetting<NamedBuildQuery>() {

			@Override
			public Project getProject() {
				return null;
			}

			@Override
			public User getUser() {
				return User.this;
			}

			@Override
			public ArrayList<NamedBuildQuery> getUserQueries() {
				return userBuildQueries;
			}

			@Override
			public void setUserQueries(ArrayList<NamedBuildQuery> userQueries) {
				userBuildQueries = userQueries;
			}

			@Override
			public QueryWatchSupport<NamedBuildQuery> getQueryWatchSupport() {
				return null;
			}

			@Override
			public QuerySubscriptionSupport<NamedBuildQuery> getQuerySubscriptionSupport() {
				return new QuerySubscriptionSupport<NamedBuildQuery>() {

					@Override
					public LinkedHashSet<String> getUserQuerySubscriptions() {
						return userBuildQuerySubscriptions;
					}

					@Override
					public LinkedHashSet<String> getQuerySubscriptions() {
						return buildQuerySubscriptions;
					}
					
				};
			}
			
		};
	}
	
	@Override
    public PrincipalCollection getPrincipals() {
        return new SimplePrincipalCollection(getId(), "");
    }
    
    @Override
    public Object getCredentials() {
    	return password;
    }

    public Subject asSubject() {
    	return SecurityUtils.asSubject(getId());
    }

	@Editable(name="Login Name", order=100)
	@UserName
	@NotEmpty
	@Override
	public String getName() {
		return name;
	}
	
    public void setName(String name) {
    	this.name = name;
    }
    
	@Editable(order=150)
	@Password(needConfirm=true, autoComplete="new-password")
	@NotEmpty
	public String getPassword() {
		return password;
	}

    /**
     * Set password of this user. 
     * 
     * @param password
     * 			password to set
     */
    public void setPassword(String password) {
    	this.password = password;
    }

    public boolean isExternalManaged() {
    	return getPassword().equals(EXTERNAL_MANAGED);
    }
    
	@Editable(order=200)
	public String getFullName() {
		return fullName;
	}

	public void setFullName(String fullName) {
		this.fullName = fullName;
	}
	
	public SsoInfo getSsoInfo() {
		return ssoInfo;
	}

	public void setSsoInfo(SsoInfo ssoInfo) {
		this.ssoInfo = ssoInfo;
	}

	public String getAccessToken() {
		return accessToken;
	}

	public void setAccessToken(String accessToken) {
		this.accessToken = accessToken;
	}

	@Editable(order=300)
	@NotEmpty
	@Email
	public String getEmail() {
		return email;
	}

	public void setEmail(String email) {
		this.email = email;
	}

	@Editable(order=350, description="Specify an email to use for web based git operations if you want to "
			+ "keep your primary email secret")
	public String getGitEmail() {
		return gitEmail;
	}

	public void setGitEmail(String gitEmail) {
		this.gitEmail = gitEmail;
	}

	@Editable(order=400, description="Optionally specify one or more alternate emails with one email per line. "
			+ "With alternate emails, git commits authored/committed via your old emails can be associated with "
			+ "your current account")
	@EmailList
	public ArrayList<String> getAlternateEmails() {
		return alternateEmails;
	}

	public void setAlternateEmails(ArrayList<String> alternateEmails) {
		this.alternateEmails = alternateEmails;
	}
	
	public Collection<Membership> getMemberships() {
		return memberships;
	}

	public void setMemberships(Collection<Membership> memberships) {
		this.memberships = memberships;
	}
	
	public Collection<String> getEmails() {
		Collection<String> emails = Sets.newHashSet(email);
		if (gitEmail != null)
			emails.add(gitEmail);
		emails.addAll(alternateEmails);
		return emails;
	}

	@Override
	public String toString() {
		return MoreObjects.toStringHelper(this)
				.add("name", getName())
				.toString();
	}
	
	public PersonIdent asPerson() {
		if (isSystem())
			return new PersonIdent(getDisplayName(), "");
		else if (getGitEmail() != null)
			return new PersonIdent(getDisplayName(), getGitEmail());
		else
			return new PersonIdent(getDisplayName(), getEmail());
	}
	
	public String getDisplayName() {
		if (getFullName() != null)
			return getFullName();
		else
			return getName();
	}
	
	public boolean isRoot() {
		return ROOT_ID.equals(getId());
	}

	public boolean isSystem() {
		return SYSTEM_ID.equals(getId());
	}
	
	public boolean isUnknown() {
		return UNKNOWN_ID.equals(getId());
	}
	
	public Collection<UserAuthorization> getAuthorizations() {
		return authorizations;
	}

	public void setAuthorizations(Collection<UserAuthorization> authorizations) {
		this.authorizations = authorizations;
	}

	@Override
	public int compareTo(AbstractEntity entity) {
		User user = (User) entity;
		if (getDisplayName().equals(user.getDisplayName())) {
			return getId().compareTo(entity.getId());
		} else {
			return getDisplayName().compareTo(user.getDisplayName());
		}
	}

	public double getMatchScore(@Nullable String queryTerm) {
		double scoreOfName = MatchScoreUtils.getMatchScore(name, queryTerm);
		double scoreOfFullName = MatchScoreUtils.getMatchScore(fullName, queryTerm);
		return Math.max(scoreOfName, scoreOfFullName);
	}

	public Collection<Group> getGroups() {
		if (groups == null)  
			groups = getMemberships().stream().map(it->it.getGroup()).collect(Collectors.toList());
		return groups;
	}
	
	public static void push(User user) {
		stack.get().push(user);
	}

	public static void pop() {
		stack.get().pop();
	}
	
	@Nullable
	public static User get() {
		if (!stack.get().isEmpty())
			return stack.get().peek();
		else 
			return SecurityUtils.getUser();
	}

    public Collection<SshKey> getSshKeys() {
        return sshKeys;
    }

    public void setSshKeys(Collection<SshKey> sshKeys) {
        this.sshKeys = sshKeys;
    }
    
    public boolean isSshKeyExternalManaged() {
    	if (isExternalManaged()) {
    		if (getSsoInfo().getConnector() != null) {
    			return false;
    		} else {
	    		Authenticator authenticator = OneDev.getInstance(SettingManager.class).getAuthenticator();
	    		return authenticator != null && authenticator.isManagingSshKeys();
    		}
    	} else {
    		return false;
    	}
    }
    
    public boolean isMembershipExternalManaged() {
    	if (isExternalManaged()) {
    		SettingManager settingManager = OneDev.getInstance(SettingManager.class);
    		if (getSsoInfo().getConnector() != null) {
    			SsoConnector ssoConnector = settingManager.getSsoConnectors().stream()
    					.filter(it->it.getName().equals(getSsoInfo().getConnector()))
    					.findFirst().orElse(null);
    			return ssoConnector != null && ssoConnector.isManagingMemberships();
    		} else {
	    		Authenticator authenticator = settingManager.getAuthenticator();
	    		return authenticator != null && authenticator.isManagingMemberships();
    		}
    	} else {
    		return false;
    	}
    }

    public String getAuthSource() {
		if (isExternalManaged()) {
			if (getSsoInfo().getConnector() != null)
				return AUTH_SOURCE_SSO_PROVIDER + getSsoInfo().getConnector();
			else
				return AUTH_SOURCE_EXTERNAL_AUTHENTICATOR;
		} else {
			return AUTH_SOURCE_BUILTIN_USER_STORE;
		}
    }

	public Collection<PullRequestReview> getPullRequestReviews() {
		return pullRequestReviews;
	}

	public void setPullRequestReviews(Collection<PullRequestReview> pullRequestReviews) {
		this.pullRequestReviews = pullRequestReviews;
	}

	public Collection<PullRequestAssignment> getPullRequestAssignments() {
		return pullRequestAssignments;
	}

	public void setPullRequestAssignments(Collection<PullRequestAssignment> pullRequestAssignments) {
		this.pullRequestAssignments = pullRequestAssignments;
	}

	public Collection<PullRequestWatch> getPullRequestWatches() {
		return pullRequestWatches;
	}

	public void setPullRequestWatches(Collection<PullRequestWatch> pullRequestWatches) {
		this.pullRequestWatches = pullRequestWatches;
	}

	public Collection<IssueWatch> getIssueWatches() {
		return issueWatches;
	}

	public void setIssueWatches(Collection<IssueWatch> issueWatches) {
		this.issueWatches = issueWatches;
	}

	public Collection<IssueVote> getIssueVotes() {
		return issueVotes;
	}

	public void setIssueVotes(Collection<IssueVote> issueVotes) {
		this.issueVotes = issueVotes;
	}

	public Collection<IssueQuerySetting> getProjectIssueQuerySettings() {
		return projectIssueQuerySettings;
	}

	public void setProjectIssueQuerySettings(Collection<IssueQuerySetting> projectIssueQuerySettings) {
		this.projectIssueQuerySettings = projectIssueQuerySettings;
	}

	public Collection<BuildQuerySetting> getProjectBuildQuerySettings() {
		return projectBuildQuerySettings;
	}

	public void setProjectBuildQuerySettings(Collection<BuildQuerySetting> projectBuildQuerySettings) {
		this.projectBuildQuerySettings = projectBuildQuerySettings;
	}

	public Collection<PullRequestQuerySetting> getProjectPullRequestQuerySettings() {
		return projectPullRequestQuerySettings;
	}

	public void setProjectPullRequestQuerySettings(Collection<PullRequestQuerySetting> projectPullRequestQuerySettings) {
		this.projectPullRequestQuerySettings = projectPullRequestQuerySettings;
	}

	public Collection<CommitQuerySetting> getProjectCommitQuerySettings() {
		return projectCommitQuerySettings;
	}

	public void setProjectCommitQuerySettings(Collection<CommitQuerySetting> projectCommitQuerySettings) {
		this.projectCommitQuerySettings = projectCommitQuerySettings;
	}

	public Collection<CodeCommentQuerySetting> getProjectCodeCommentQuerySettings() {
		return projectCodeCommentQuerySettings;
	}

	public void setProjectCodeCommentQuerySettings(Collection<CodeCommentQuerySetting> projectCodeCommentQuerySettings) {
		this.projectCodeCommentQuerySettings = projectCodeCommentQuerySettings;
	}

	public ArrayList<NamedProjectQuery> getUserProjectQueries() {
		return userProjectQueries;
	}

	public void setUserProjectQueries(ArrayList<NamedProjectQuery> userProjectQueries) {
		this.userProjectQueries = userProjectQueries;
	}

	public ArrayList<NamedIssueQuery> getUserIssueQueries() {
		return userIssueQueries;
	}

	public void setUserIssueQueries(ArrayList<NamedIssueQuery> userIssueQueries) {
		this.userIssueQueries = userIssueQueries;
	}

	public LinkedHashMap<String, Boolean> getUserIssueQueryWatches() {
		return userIssueQueryWatches;
	}

	public void setUserIssueQueryWatches(LinkedHashMap<String, Boolean> userIssueQueryWatches) {
		this.userIssueQueryWatches = userIssueQueryWatches;
	}

	public LinkedHashMap<String, Boolean> getIssueQueryWatches() {
		return issueQueryWatches;
	}

	public void setIssueQueryWatches(LinkedHashMap<String, Boolean> issueQueryWatches) {
		this.issueQueryWatches = issueQueryWatches;
	}

	public ArrayList<NamedPullRequestQuery> getUserPullRequestQueries() {
		return userPullRequestQueries;
	}

	public void setUserPullRequestQueries(ArrayList<NamedPullRequestQuery> userPullRequestQueries) {
		this.userPullRequestQueries = userPullRequestQueries;
	}

	public LinkedHashMap<String, Boolean> getUserPullRequestQueryWatches() {
		return userPullRequestQueryWatches;
	}

	public void setUserPullRequestQueryWatches(LinkedHashMap<String, Boolean> userPullRequestQueryWatches) {
		this.userPullRequestQueryWatches = userPullRequestQueryWatches;
	}

	public LinkedHashMap<String, Boolean> getPullRequestQueryWatches() {
		return pullRequestQueryWatches;
	}

	public void setPullRequestQueryWatches(LinkedHashMap<String, Boolean> pullRequestQueryWatches) {
		this.pullRequestQueryWatches = pullRequestQueryWatches;
	}

	public ArrayList<NamedBuildQuery> getUserBuildQueries() {
		return userBuildQueries;
	}

	public void setUserBuildQueries(ArrayList<NamedBuildQuery> userBuildQueries) {
		this.userBuildQueries = userBuildQueries;
	}

	public LinkedHashSet<String> getUserBuildQuerySubscriptions() {
		return userBuildQuerySubscriptions;
	}
	
	public void setUserBuildQuerySubscriptions(LinkedHashSet<String> userBuildQuerySubscriptions) {
		this.userBuildQuerySubscriptions = userBuildQuerySubscriptions;
	}

	public LinkedHashSet<String> getBuildQuerySubscriptions() {
		return buildQuerySubscriptions;
	}

	public void setBuildQuerySubscriptions(LinkedHashSet<String> buildQuerySubscriptions) {
		this.buildQuerySubscriptions = buildQuerySubscriptions;
	}

}
