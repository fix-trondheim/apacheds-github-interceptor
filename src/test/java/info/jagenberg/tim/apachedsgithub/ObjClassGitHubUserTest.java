package info.jagenberg.tim.apachedsgithub;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.io.IOException;

import org.apache.directory.api.ldap.model.entry.Entry;
import org.apache.directory.api.ldap.model.exception.LdapException;
import org.apache.directory.ldap.client.api.LdapConnection;
import org.apache.directory.ldap.client.api.LdapNetworkConnection;
import org.apache.directory.server.annotations.CreateLdapServer;
import org.apache.directory.server.annotations.CreateTransport;
import org.apache.directory.server.core.annotations.ApplyLdifFiles;
import org.apache.directory.server.core.annotations.ContextEntry;
import org.apache.directory.server.core.annotations.CreateDS;
import org.apache.directory.server.core.annotations.CreatePartition;
import org.apache.directory.server.core.integ.AbstractLdapTestUnit;
import org.apache.directory.server.core.integ.FrameworkRunner;
import org.apache.directory.server.ldap.LdapServer;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(FrameworkRunner.class)
@CreateDS(partitions = { @CreatePartition(name = "example", suffix = "dc=example,dc=com", contextEntry = @ContextEntry(entryLdif = "dn: dc=example,dc=com\ndc: example\nobjectClass: top\nobjectClass: domain\n\n")) })
@CreateLdapServer(transports = { @CreateTransport(protocol = "LDAP") })
@ApplyLdifFiles({ "github.ldif" })
public class ObjClassGitHubUserTest extends AbstractLdapTestUnit {

	private LdapConnection getConnection() throws LdapException {
		LdapServer server = getLdapServer();
		LdapConnection connection = new LdapNetworkConnection("localhost", server.getPort());
		connection.bind("uid=admin,ou=system", "secret");
		connection.loadSchema();
		return connection;
	}

	@Test
	@ApplyLdifFiles({ "test-data.ldif" })
	public void testIsObjectClassGitHubUser() throws LdapException, IOException {
		LdapConnection connection = getConnection();

		Entry entryWithObjectClassGitHubUser = connection.lookup("uid=testGitHubUserAllSet,dc=example,dc=com");
		assertTrue("testGitHubUserAllSet wrongly detected invalid", ObjClassGitHubUser.isObjectClassGitHubUser(entryWithObjectClassGitHubUser));

		Entry entryMissingObjectClassGitHubUser = connection.lookup("uid=testUser,dc=example,dc=com");
		assertFalse("testUser wrongly detected as valid GitHubUser", ObjClassGitHubUser.isObjectClassGitHubUser(entryMissingObjectClassGitHubUser));

		connection.close();
	}

	@Test
	@ApplyLdifFiles({ "test-data.ldif" })
	public void testhasAllGitHubAttributesSet() throws LdapException, IOException {
		LdapConnection connection = getConnection();

		Entry entryAllAttrsSet = connection.lookup("uid=testGitHubUserAllSet,dc=example,dc=com");
		assertTrue(ObjClassGitHubUser.hasAllGitHubAttributesSet(entryAllAttrsSet));

		Entry entryNoAttrsSet = connection.lookup("uid=testGitHubUserUnSet,dc=example,dc=com");
		assertFalse(ObjClassGitHubUser.hasAllGitHubAttributesSet(entryNoAttrsSet));

		connection.close();
	}

	@Test
	@ApplyLdifFiles({ "test-data.ldif" })
	public void testGetUser() throws LdapException, IOException {
		LdapConnection connection = getConnection();

		Entry entryAllAttrsSet = connection.lookup("uid=testGitHubUserAllSet,dc=example,dc=com");
		assertEquals("FIX-TestUser123", ObjClassGitHubUser.getUser(entryAllAttrsSet));

		Entry entryNoAttrsSet = connection.lookup("uid=testGitHubUserUnSet,dc=example,dc=com");
		try {
			ObjClassGitHubUser.getUser(entryNoAttrsSet);
			fail("Accessing unset attribute did not result in error");
		} catch (IllegalArgumentException e) {
			assertTrue("Wrong exception thrown", e.getMessage().contains(ObjClassGitHubUser.ATTR_LITERALS.get(ObjClassGitHubUser.GITHUB_USER_ATTR_ID)));
		}

		connection.close();
	}

	@Test
	@ApplyLdifFiles({ "test-data.ldif" })
	public void testGetTeams() throws LdapException, IOException {
		LdapConnection connection = getConnection();

		Entry entryOneTeamSet = connection.lookup("uid=testGitHubUserAllSet,dc=example,dc=com");
		assertArrayEquals(new String[] { "members" }, ObjClassGitHubUser.getTeams(entryOneTeamSet).toArray());

		Entry entryNoTeamSet = connection.lookup("uid=testGitHubUserUnSet,dc=example,dc=com");
		try {
			ObjClassGitHubUser.getTeams(entryNoTeamSet);
			fail("Accessing unset attribute did not result in error");
		} catch (IllegalArgumentException e) {
			assertTrue("Wrong exception thrown", e.getMessage().contains(ObjClassGitHubUser.ATTR_LITERALS.get(ObjClassGitHubUser.GITHUB_TEAM_ATTR_ID)));
		}

		Entry entryMultipleTeamsSet = connection.lookup("uid=testGitHubUserAllSetMultiTeam,dc=example,dc=com");
		assertArrayEquals(new String[] { "members", "alumni" }, ObjClassGitHubUser.getTeams(entryMultipleTeamsSet).toArray());

		connection.close();
	}

	@Test
	@ApplyLdifFiles({ "test-data.ldif" })
	public void testGetOrg() throws LdapException, IOException {
		LdapConnection connection = getConnection();

		Entry entryAllAttrsSet = connection.lookup("uid=testGitHubUserAllSet,dc=example,dc=com");
		assertEquals("fix-trondheim", ObjClassGitHubUser.getOrg(entryAllAttrsSet));

		Entry entryNoAttrsSet = connection.lookup("uid=testGitHubUserUnSet,dc=example,dc=com");
		try {
			ObjClassGitHubUser.getOrg(entryNoAttrsSet);
			fail("Accessing unset attribute did not result in error");
		} catch (IllegalArgumentException e) {
			assertTrue("Wrong exception thrown", e.getMessage().contains(ObjClassGitHubUser.ATTR_LITERALS.get(ObjClassGitHubUser.GITHUB_ORG_ATTR_ID)));
		}

		connection.close();
	}

}