package info.jagenberg.tim.apachedsgithub;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import org.apache.commons.collections4.ListUtils;
import org.apache.directory.api.ldap.model.entry.Entry;
import org.apache.directory.api.ldap.model.entry.Modification;
import org.apache.directory.api.ldap.model.entry.Value;
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

	public void setGitHubConnector(GitHubConnector gitHubConnector) {
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
	public void modify(final ModifyOperationContext modifyContext) throws LdapException {
		Entry entry = modifyContext.getEntry();
		if (ObjClassGitHubUser.isObjectClassGitHubUser(entry)) {
			String oldUser = "";
			List<String> oldTeams = new ArrayList<>();
			String oldOrg = "";
			try {
				oldUser = ObjClassGitHubUser.getUser(entry);
			} catch (IllegalArgumentException e) {
				// leave empty if not set
			}
			try {
				oldTeams = ObjClassGitHubUser.getTeams(entry);
			} catch (IllegalArgumentException e) {
				// leave empty if not set
			}
			try {
				oldOrg = ObjClassGitHubUser.getOrg(entry);
			} catch (IllegalArgumentException e) {
				// leave empty if not set
			}
			String newUser = getNewUser(modifyContext.getModItems(), oldUser);
			List<String> newTeams = getNewTeams(oldTeams, modifyContext.getModItems(), oldTeams);
			String newOrg = getNewOrg(modifyContext.getModItems(), oldOrg);
			boolean userChanged = oldUser != newUser;
			List<String> addedTeams = ListUtils.subtract(newTeams, oldTeams);
			List<String> removedTeams = ListUtils.subtract(oldTeams, newTeams);
			boolean orgChanged = oldOrg != newOrg;
			if (userChanged || orgChanged) {
				updateUserOrgTeams(oldUser, oldTeams, oldOrg, newUser, newTeams, newOrg);
			} else {
				if (!addedTeams.isEmpty() || !removedTeams.isEmpty()) {
					updateUserOrgTeams(oldUser, removedTeams, oldOrg, newUser, addedTeams, newOrg);
				}
			}
		}
		next(modifyContext);
	}

	@Override
	public void delete(final DeleteOperationContext deleteContext) throws LdapException {
		Entry entry = deleteContext.getEntry();
		if (ObjClassGitHubUser.isObjectClassGitHubUser(entry)) {
			try {
				String user = ObjClassGitHubUser.getUser(entry);
				List<String> teams = ObjClassGitHubUser.getTeams(entry);
				String org = ObjClassGitHubUser.getOrg(entry);
				teams.stream().forEach(t -> removeUserFromTeam(user, t, org));
			} catch (IllegalArgumentException e) {
				// don't interact with github if not all arguments are valid/set
			}
		}
		next(deleteContext);
	}

	private void removeUserFromTeam(String user, String t, String org) {
		try {
			gitHubConnector.removeUser(user, t, org);
		} catch (IllegalArgumentException e) {
			// ignore if we couldn't remove a user
			LOG.debug("Could not remove " + user + " from " + t + " in " + org);
		}
	}

	private void updateUserOrgTeams(String oldUser, List<String> oldTeams, String oldOrg, String newUser, List<String> newTeams, String newOrg) {
		newTeams.stream().forEach(nt -> gitHubConnector.addUser(newUser, nt, newOrg));
		oldTeams.stream().forEach(ot -> {
			try {
				gitHubConnector.removeUser(oldUser, ot, oldOrg);
			} catch (IllegalArgumentException e) {
				LOG.debug("Could not remove GitHub user");
			}
		});
	}

	private String getNewUser(List<Modification> mods, String defaultUser) {
		List<String> newValues = getNewValues(new ArrayList<>(), mods, ObjClassGitHubUser.GITHUB_USER_ATTR_ID, 1);
		if (newValues.isEmpty()) {
			return defaultUser;
		} else {
			return newValues.get(0);
		}
	}

	private String getNewOrg(List<Modification> mods, String defaultOrg) {
		List<String> newValues = getNewValues(new ArrayList<>(), mods, ObjClassGitHubUser.GITHUB_ORG_ATTR_ID, 1);
		if (newValues.isEmpty()) {
			return defaultOrg;
		} else {
			return newValues.get(0);
		}
	}

	private List<String> getNewTeams(List<String> origTeams, List<Modification> mods, List<String> defaultTeams) {
		List<String> newValues = getNewValues(origTeams, mods, ObjClassGitHubUser.GITHUB_TEAM_ATTR_ID, Integer.MAX_VALUE);
		if (newValues.isEmpty()) {
			return defaultTeams;
		} else {
			return newValues;
		}
	}

	private List<String> getNewValues(List<String> origValues, List<Modification> mods, String oid, int max) {
		List<String> newValues = new ArrayList<>(origValues);
		Stream<Modification> oidMods = mods.stream().filter(mod -> mod.getAttribute().getId().equals(oid));
		oidMods.forEach(mod -> applyMod(mod, newValues));
		if (newValues.size() > max) {
			throw new IllegalArgumentException("Only " + max + " " + ObjClassGitHubUser.ATTR_LITERALS.get(oid) + " allowed");
		}
		return newValues;
	}

	private void applyMod(Modification mod, List<String> newValues) {
		switch (mod.getOperation()) {
		case ADD_ATTRIBUTE:
			addAttributeValues(mod, newValues);
			break;

		case REMOVE_ATTRIBUTE:
			removeAttributeValues(mod, newValues);
			break;

		case REPLACE_ATTRIBUTE:
			replaceAttributeValues(mod, newValues);
			break;

		default:
			break;
		}
	}

	private void addAttributeValues(Modification mod, List<String> newValues) {
		for (Value<?> val : mod.getAttribute()) {
			newValues.add(val.getString());
		}
	}

	private void removeAttributeValues(Modification mod, List<String> newValues) {
		for (Value<?> val : mod.getAttribute()) {
			newValues.remove(val.getString());
		}
	}

	private void replaceAttributeValues(Modification mod, List<String> newValues) {
		newValues.clear();
		for (Value<?> val : mod.getAttribute()) {
			newValues.add(val.getString());
		}
	}

}
