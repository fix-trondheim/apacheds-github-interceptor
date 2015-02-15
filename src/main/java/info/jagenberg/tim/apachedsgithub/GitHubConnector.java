package info.jagenberg.tim.apachedsgithub;

import java.io.IOException;
import java.util.Optional;
import java.util.stream.Stream;

import org.kohsuke.github.GHOrganization;
import org.kohsuke.github.GHTeam;
import org.kohsuke.github.GHUser;
import org.kohsuke.github.GitHub;

public class GitHubConnector {

	private GitHub github;
	private boolean connected;

	public GitHubConnector() {
		try {
			github = GitHub.connectUsingOAuth(System.getProperty("githubinterceptor.oauthtoken"));
			connected = true;
		} catch (IOException e) {
			connected = false;
		}
	}

	public void addUser(String user, String team, String org) {
		if (connected) {
			GHOrganization ghOrg = getOrg(org);
			GHTeam ghTeam = getTeam(ghOrg, team);
			GHUser ghUser = getUser(user);
			try {
				ghTeam.add(ghUser);
			} catch (IOException e) {
				throw new IllegalArgumentException("Could not add " + user + " to " + team + " in " + org, e);
			}
		} else {
			throw new IllegalStateException("Could not connect to GitHub");
		}
	}

	public void removeUser(String user, String team, String org) {
		if (connected) {
			GHOrganization ghOrg = getOrg(org);
			GHTeam ghTeam = getTeam(ghOrg, team);
			GHUser ghUser = getUser(user);
			try {
				ghTeam.remove(ghUser);
			} catch (IOException e) {
				throw new IllegalArgumentException("Could not remove " + user + " from " + team + " in " + org, e);
			}
		} else {
			throw new IllegalStateException("Could not connect to GitHub");
		}
	}

	public boolean hasUser(String user, String team, String org) {
		if (connected) {
			try {
				GHOrganization ghOrg = getOrg(org);
				GHTeam ghTeam = getTeam(ghOrg, team);
				GHUser ghUser = getUser(user);
				return ghTeam.hasMember(ghUser);
			} catch (IllegalArgumentException e) {
				return false;
			}
		} else {
			throw new IllegalStateException("Could not connect to GitHub");
		}
	}

	public boolean hasUser(String user) {
		if (connected) {
			try {
				github.getUser(user);
				return true;
			} catch (IOException e) {
			}
		} else {
			throw new IllegalStateException("Could not connect to GitHub");
		}
		return false;
	}

	private GHOrganization getOrg(String org) {
		GHOrganization ghOrg = null;
		try {
			ghOrg = github.getOrganization(org);
			if (ghOrg == null) {
				throw new IllegalArgumentException("Could not find organization " + org);
			}
		} catch (IOException e) {
			throw new IllegalArgumentException("Could not find organization " + org, e);
		}
		return ghOrg;
	}

	private GHTeam getTeam(GHOrganization ghOrg, String team) {
		GHTeam ghTeam = null;
		try {
			Stream<GHTeam> teamsStream = ghOrg.getTeams().values().stream();
			Optional<GHTeam> ghTeamOpt = teamsStream.filter(t -> t.getName().equalsIgnoreCase(team)).findFirst();
			if (!ghTeamOpt.isPresent()) {
				throw new IllegalArgumentException("Could not find team " + team);
			}
			ghTeam = ghTeamOpt.get();
		} catch (IOException e) {
			throw new IllegalArgumentException("Could not find team " + team, e);
		}
		return ghTeam;
	}

	private GHUser getUser(String user) {
		GHUser ghUser = null;
		try {
			ghUser = github.getUser(user);
			if (ghUser == null) {
				throw new IllegalArgumentException("Could not find user " + user);
			}
		} catch (IOException e) {
			throw new IllegalArgumentException("Could not find user " + user, e);
		}
		return ghUser;
	}

}
