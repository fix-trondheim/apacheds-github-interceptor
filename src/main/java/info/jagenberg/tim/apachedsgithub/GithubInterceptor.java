package info.jagenberg.tim.apachedsgithub;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import org.apache.directory.api.ldap.model.entry.Entry;
import org.apache.directory.api.ldap.model.entry.Modification;
import org.apache.directory.api.ldap.model.entry.ModificationOperation;
import org.apache.directory.api.ldap.model.exception.LdapException;
import org.apache.directory.server.core.api.interceptor.BaseInterceptor;
import org.apache.directory.server.core.api.interceptor.context.AddOperationContext;
import org.apache.directory.server.core.api.interceptor.context.DeleteOperationContext;
import org.apache.directory.server.core.api.interceptor.context.ModifyOperationContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class GithubInterceptor extends BaseInterceptor {

	private static final Logger LOG = LoggerFactory.getLogger(GithubInterceptor.class);

	private GitHubConnector gitHubConnector;

	public GithubInterceptor() {
		super();
		gitHubConnector = new GitHubConnector();
	}

	public GithubInterceptor(GitHubConnector gitHubConnector) {
		super();
		this.gitHubConnector = gitHubConnector;
	}

	@Override
	public void add(final AddOperationContext addContext) throws LdapException {
		Entry entry = addContext.getEntry();
		if (ObjClassGitHubUser.isObjectClassGitHubUser(entry)) {
			try {
				String user = ObjClassGitHubUser.getUser(entry);
				List<String> teams = ObjClassGitHubUser.getTeams(entry);
				String org = ObjClassGitHubUser.getOrg(entry);
				teams.stream().forEach(t -> gitHubConnector.addUser(user, t, org));
			} catch (IllegalArgumentException e) {
				// don't interact with github if not all arguments are valid/set
			}
		}
		next(addContext);
	}

	@Override
	public void delete(final DeleteOperationContext deleteContext) throws LdapException {
		Entry entry = deleteContext.getEntry();
		if (ObjClassGitHubUser.isObjectClassGitHubUser(entry)) {
			try {
				String user = ObjClassGitHubUser.getUser(entry);
				List<String> teams = ObjClassGitHubUser.getTeams(entry);
				String org = ObjClassGitHubUser.getOrg(entry);
				teams.stream().forEach(t -> gitHubConnector.removeUser(user, t, org));
			} catch (IllegalArgumentException e) {
				// don't interact with github if not all arguments are valid/set
			}
		}
		next(deleteContext);
	}

	@Override
	public void modify(final ModifyOperationContext modifyContext) throws LdapException {
		Entry entry = modifyContext.getEntry();
		if (ObjClassGitHubUser.isObjectClassGitHubUser(entry)) {
			try {
				String oldUser = ObjClassGitHubUser.getUser(entry);
				List<String> oldTeams = ObjClassGitHubUser.getTeams(entry);
				String oldOrg = ObjClassGitHubUser.getOrg(entry);
				String newUser = getNewUser(modifyContext.getModItems().stream(), oldUser);
				List<String> newTeams = getNewTeams(modifyContext.getModItems().stream(), oldTeams);
				String newOrg = getNewOrg(modifyContext.getModItems().stream(), oldOrg);
				// TODO this update logic needs to be improved! 
				boolean somethingChanged = oldUser != newUser || !oldTeams.containsAll(newTeams) || !newTeams.containsAll(oldTeams) || oldOrg != newOrg;
				if (somethingChanged) {
					oldTeams.stream().forEach(ot -> gitHubConnector.removeUser(oldUser, ot, oldOrg));
					newTeams.stream().forEach(nt -> gitHubConnector.addUser(newUser, nt, newOrg));
				}
			} catch (IllegalArgumentException e) {
				// don't interact with github if not all arguments are valid/set
			}
		}
		next(modifyContext);
	}

	private String getNewUser(Stream<Modification> modItems, String defaultUser) {
		List<String> newValues = getNewValues(modItems, ObjClassGitHubUser.GITHUB_USER_ATTR_ID, 1);
		if (newValues.isEmpty()) {
			return defaultUser;
		} else {
			return newValues.get(0);
		}
	}

	private String getNewOrg(Stream<Modification> modItems, String defaultOrg) {
		List<String> newValues = getNewValues(modItems, ObjClassGitHubUser.GITHUB_ORG_ATTR_ID, 1);
		if (newValues.isEmpty()) {
			return defaultOrg;
		} else {
			return newValues.get(0);
		}
	}

	private List<String> getNewTeams(Stream<Modification> modItems, List<String> defaultTeams) {
		List<String> newValues = getNewValues(modItems, ObjClassGitHubUser.GITHUB_TEAM_ATTR_ID, Integer.MAX_VALUE);
		if (newValues.isEmpty()) {
			return defaultTeams;
		} else {
			return newValues;
		}
	}

	private List<String> getNewValues(Stream<Modification> modItems, String oid, int max) {
		List<String> newValues = new ArrayList<>();
		Stream<Modification> userMods = modItems.filter(mod -> mod.getAttribute().getId().equals(oid));
		userMods.forEach(mod -> {
			switch (mod.getOperation()) {
			case ADD_ATTRIBUTE:
				try {
					newValues.add(mod.getAttribute().getString());
				} catch (Exception e) {
					LOG.error("getString should have been applicable");
				}
				break;

			case REMOVE_ATTRIBUTE:
				newValues.add("");
				break;

			case REPLACE_ATTRIBUTE:
				try {
					newValues.add(mod.getAttribute().getString());
				} catch (Exception e) {
					LOG.error("getString should have been applicable");
				}
				break;

			default:
				break;
			}
		});
		if (newValues.size() > max) {
			throw new IllegalArgumentException("Only " + max + " " + ObjClassGitHubUser.attrLiterals.get(oid) + " allowed");
		}
		return newValues;
	}

}
