package info.jagenberg.tim.apachedsgithub;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

import java.io.IOException;
import java.net.URL;
import java.util.List;

import org.apache.directory.api.ldap.model.entry.DefaultModification;
import org.apache.directory.api.ldap.model.entry.ModificationOperation;
import org.apache.directory.api.ldap.model.exception.LdapException;
import org.apache.directory.api.ldap.model.ldif.LdifEntry;
import org.apache.directory.api.ldap.model.ldif.LdifReader;
import org.apache.directory.ldap.client.api.LdapConnection;
import org.apache.directory.ldap.client.api.LdapNetworkConnection;
import org.apache.directory.server.annotations.CreateLdapServer;
import org.apache.directory.server.annotations.CreateTransport;
import org.apache.directory.server.core.annotations.ApplyLdifFiles;
import org.apache.directory.server.core.annotations.ContextEntry;
import org.apache.directory.server.core.annotations.CreateDS;
import org.apache.directory.server.core.annotations.CreatePartition;
import org.apache.directory.server.core.api.interceptor.Interceptor;
import org.apache.directory.server.core.integ.AbstractLdapTestUnit;
import org.apache.directory.server.core.integ.FrameworkRunner;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InOrder;
import org.mockito.Mockito;

@RunWith(FrameworkRunner.class)
@CreateDS(partitions = { @CreatePartition(name = "example", suffix = "dc=example,dc=com", contextEntry = @ContextEntry(entryLdif = "dn: dc=example,dc=com\ndc: example\nobjectClass: top\nobjectClass: domain\n\n")) })
@CreateLdapServer(transports = { @CreateTransport(protocol = "LDAP") })
@ApplyLdifFiles({ "github.ldif" })
public class GithubInterceptorTest extends AbstractLdapTestUnit {

	private LdapConnection connection = null;
	private GithubInterceptor interceptor;

	@Before
	public void setUp() throws Exception {
		List<Interceptor> interceptors = getService().getInterceptors();
		interceptor = new GithubInterceptor();
		interceptor.init(getService());
		interceptors.add(15, interceptor);
		getService().setInterceptors(interceptors);
	}

	private LdapConnection getConnection() throws LdapException {
		if (connection == null) {
			connection = new LdapNetworkConnection("localhost", getLdapServer().getPort());
			connection.bind("uid=admin,ou=system", "secret");
			connection.loadSchema();
		}
		return connection;
	}

	private void closeConnection() throws IOException {
		if (connection != null) {
			connection.close();
			connection = null;
		}
	}

	@Test
	public void testAddUser() throws LdapException, IOException {
		GitHubConnector connector = Mockito.mock(GitHubConnector.class);
		interceptor.setGitHubConnector(connector);

		URL testFile = getClass().getResource("/testUser.ldif");
	
		try (LdifReader reader = new LdifReader(testFile.getPath())) {
			LdifEntry entry = reader.next();
			getConnection().add(entry.getEntry());
		}
	
		verify(connector, never()).addUser("", "", "");
		verifyNoMoreInteractions(connector);
	
		closeConnection();
	}

	@Test
	public void testAddGitHubUserAllSet() throws LdapException, IOException {
		GitHubConnector connector = Mockito.mock(GitHubConnector.class);
		interceptor.setGitHubConnector(connector);
		
		URL testFile = getClass().getResource("/testGitHubUserAllSet.ldif");

		try (LdifReader reader = new LdifReader(testFile.getPath())) {
			LdifEntry entry = reader.next();
			getConnection().add(entry.getEntry());
		}

		verify(connector).addUser("FIX-TestUser123", "members", "fix-trondheim");
		verifyNoMoreInteractions(connector);

		closeConnection();
	}

	@Test
	public void testAddGitHubUserAllSetMultiTeam() throws LdapException, IOException {
		GitHubConnector connector = Mockito.mock(GitHubConnector.class);
		interceptor.setGitHubConnector(connector);
		
		URL testFile = getClass().getResource("/testGitHubUserAllSetMultiTeam.ldif");

		try (LdifReader reader = new LdifReader(testFile.getPath())) {
			LdifEntry entry = reader.next();
			getConnection().add(entry.getEntry());
		}

		InOrder inOrder = inOrder(connector);
		inOrder.verify(connector).addUser("FIX-TestUser123", "members", "fix-trondheim");
		inOrder.verify(connector).addUser("FIX-TestUser123", "alumni", "fix-trondheim");
		verifyNoMoreInteractions(connector);

		closeConnection();
	}

	@Test
	public void testAddGitHubUserUnSet() throws LdapException, IOException {
		GitHubConnector connector = Mockito.mock(GitHubConnector.class);
		interceptor.setGitHubConnector(connector);
		
		URL testFile = getClass().getResource("/testGitHubUserUnSet.ldif");

		try (LdifReader reader = new LdifReader(testFile.getPath())) {
			LdifEntry entry = reader.next();
			getConnection().add(entry.getEntry());
		}

		verify(connector, never()).addUser("", "", "");
		verifyNoMoreInteractions(connector);

		closeConnection();
	}

	@Test
	@ApplyLdifFiles({ "test-data.ldif" })
	public void testModifyGitHubUserAllSetMultiTeam() throws LdapException, IOException {
		GitHubConnector connector = Mockito.mock(GitHubConnector.class);
		interceptor.setGitHubConnector(connector);

		DefaultModification modification = new DefaultModification(ModificationOperation.REPLACE_ATTRIBUTE, ObjClassGitHubUser.GITHUB_USER_ATTR_ID, "Test123");
		getConnection().modify("uid=testGitHubUserAllSetMultiTeam,dc=example,dc=com", modification);
		
		InOrder inOrder = inOrder(connector);
		inOrder.verify(connector).addUser("Test123", "members", "fix-trondheim");
		inOrder.verify(connector).addUser("Test123", "alumni", "fix-trondheim");
		inOrder.verify(connector).removeUser("FIX-TestUser123", "members", "fix-trondheim");
		inOrder.verify(connector).removeUser("FIX-TestUser123", "alumni", "fix-trondheim");
		verifyNoMoreInteractions(connector);
	
		closeConnection();
	}

	@Test
	@ApplyLdifFiles({ "test-data.ldif" })
	public void testModifyGitHubUserAllSetMultiTeamWithExceptionInAdd() throws LdapException, IOException {
		GitHubConnector connector = Mockito.mock(GitHubConnector.class);
		IllegalArgumentException wrongUserException = new IllegalArgumentException("Could not find user Test123");
		doThrow(wrongUserException).when(connector).addUser(eq("Test123"), anyString(), anyString());
		interceptor.setGitHubConnector(connector);

		DefaultModification modification = new DefaultModification(ModificationOperation.REPLACE_ATTRIBUTE, ObjClassGitHubUser.GITHUB_USER_ATTR_ID, "Test123");
		try {
			getConnection().modify("uid=testGitHubUserAllSetMultiTeam,dc=example,dc=com", modification);
			fail("No exception for missing user");
		} catch (Exception e) {
			assertTrue(e.getMessage().contains("Could not find user Test123"));
		}
		
		InOrder inOrder = inOrder(connector);
		inOrder.verify(connector).addUser("Test123", "members", "fix-trondheim");
		verifyNoMoreInteractions(connector);
	
		closeConnection();
	}

	@Test
	@ApplyLdifFiles({ "test-data.ldif" })
	public void testModifyGitHubUserAllSetMultiTeamWithExceptionInRemove() throws LdapException, IOException {
		GitHubConnector connector = Mockito.mock(GitHubConnector.class);
		IllegalArgumentException wrongUserException = new IllegalArgumentException("Could not find user Test123");
		doThrow(wrongUserException).when(connector).removeUser(eq("FIX-TestUser123"), anyString(), anyString());
		interceptor.setGitHubConnector(connector);

		DefaultModification modification = new DefaultModification(ModificationOperation.REPLACE_ATTRIBUTE, ObjClassGitHubUser.GITHUB_USER_ATTR_ID, "Test123");
		getConnection().modify("uid=testGitHubUserAllSetMultiTeam,dc=example,dc=com", modification);
		
		InOrder inOrder = inOrder(connector);
		inOrder.verify(connector).addUser("Test123", "members", "fix-trondheim");
		inOrder.verify(connector).addUser("Test123", "alumni", "fix-trondheim");
		inOrder.verify(connector).removeUser("FIX-TestUser123", "members", "fix-trondheim");
		inOrder.verify(connector).removeUser("FIX-TestUser123", "alumni", "fix-trondheim");
		verifyNoMoreInteractions(connector);
	
		closeConnection();
	}

	@Test
	@ApplyLdifFiles({ "test-data.ldif" })
	public void testRemoveGitHubUserAllSet() throws LdapException, IOException {
		GitHubConnector connector = Mockito.mock(GitHubConnector.class);
		interceptor.setGitHubConnector(connector);

		getConnection().delete("uid=testGitHubUserAllSet,dc=example,dc=com");

		verify(connector).removeUser("FIX-TestUser123", "members", "fix-trondheim");
		verifyNoMoreInteractions(connector);

		closeConnection();
	}

	@Test
	@ApplyLdifFiles({ "test-data.ldif" })
	public void testRemoveGitHubUserAllSetMultiTeam() throws LdapException, IOException {
		GitHubConnector connector = Mockito.mock(GitHubConnector.class);
		interceptor.setGitHubConnector(connector);

		getConnection().delete("uid=testGitHubUserAllSetMultiTeam,dc=example,dc=com");

		InOrder inOrder = inOrder(connector);
		inOrder.verify(connector).removeUser("FIX-TestUser123", "members", "fix-trondheim");
		inOrder.verify(connector).removeUser("FIX-TestUser123", "alumni", "fix-trondheim");
		verifyNoMoreInteractions(connector);

		closeConnection();
	}

	@Test
	@ApplyLdifFiles({ "test-data.ldif" })
	public void testRemoveGitHubUserUnSet() throws LdapException, IOException {
		GitHubConnector connector = Mockito.mock(GitHubConnector.class);
		interceptor.setGitHubConnector(connector);

		getConnection().delete("uid=testGitHubUserUnSet,dc=example,dc=com");

		verify(connector, never()).removeUser("", "", "");
		verifyNoMoreInteractions(connector);

		closeConnection();
	}

	@Test
	@ApplyLdifFiles({ "test-data.ldif" })
	public void testRemoveUser() throws LdapException, IOException {
		GitHubConnector connector = Mockito.mock(GitHubConnector.class);
		interceptor.setGitHubConnector(connector);

		getConnection().delete("uid=testUser,dc=example,dc=com");

		verify(connector, never()).removeUser("", "", "");
		verifyNoMoreInteractions(connector);

		closeConnection();
	}

	@Test
	@ApplyLdifFiles({ "test-data.ldif" })
	public void testAddGitHubTeamAllSetMultiTeam() throws LdapException, IOException {
		GitHubConnector connector = Mockito.mock(GitHubConnector.class);
		interceptor.setGitHubConnector(connector);

		DefaultModification modification = new DefaultModification(ModificationOperation.ADD_ATTRIBUTE, ObjClassGitHubUser.GITHUB_TEAM_ATTR_ID, "testteam");
		getConnection().modify("uid=testGitHubUserAllSetMultiTeam,dc=example,dc=com", modification);
		
		InOrder inOrder = inOrder(connector);
		inOrder.verify(connector).addUser("FIX-TestUser123", "testteam", "fix-trondheim");
		verifyNoMoreInteractions(connector);
	
		closeConnection();
	}

	@Test
	@ApplyLdifFiles({ "test-data.ldif" })
	public void testModifyGitHubTeamAllSetMultiTeam() throws LdapException, IOException {
		GitHubConnector connector = Mockito.mock(GitHubConnector.class);
		interceptor.setGitHubConnector(connector);

		DefaultModification modification = new DefaultModification(ModificationOperation.REPLACE_ATTRIBUTE, ObjClassGitHubUser.GITHUB_TEAM_ATTR_ID, "members", "nonteam");
		getConnection().modify("uid=testGitHubUserAllSetMultiTeam,dc=example,dc=com", modification);
		
		InOrder inOrder = inOrder(connector);
		inOrder.verify(connector).addUser("FIX-TestUser123", "nonteam", "fix-trondheim");
		inOrder.verify(connector).removeUser("FIX-TestUser123", "alumni", "fix-trondheim");
		verifyNoMoreInteractions(connector);
	
		closeConnection();
	}

	@Test
	@ApplyLdifFiles({ "test-data.ldif" })
	public void testRemoveGitHubTeamAllSetMultiTeam() throws LdapException, IOException {
		GitHubConnector connector = Mockito.mock(GitHubConnector.class);
		interceptor.setGitHubConnector(connector);

		DefaultModification modification = new DefaultModification(ModificationOperation.REMOVE_ATTRIBUTE, ObjClassGitHubUser.GITHUB_TEAM_ATTR_ID, "alumni");
		getConnection().modify("uid=testGitHubUserAllSetMultiTeam,dc=example,dc=com", modification);
		
		InOrder inOrder = inOrder(connector);
		inOrder.verify(connector).removeUser("FIX-TestUser123", "alumni", "fix-trondheim");
		verifyNoMoreInteractions(connector);
	
		closeConnection();
	}

	@Test
	@ApplyLdifFiles({ "test-data.ldif" })
	public void testRemoveGitHubTeamAndModifyGitHubUserAllSetMultiTeam() throws LdapException, IOException {
		GitHubConnector connector = Mockito.mock(GitHubConnector.class);
		interceptor.setGitHubConnector(connector);

		DefaultModification modificationTeam = new DefaultModification(ModificationOperation.REMOVE_ATTRIBUTE, ObjClassGitHubUser.GITHUB_TEAM_ATTR_ID, "alumni");
		DefaultModification modificationUser = new DefaultModification(ModificationOperation.REPLACE_ATTRIBUTE, ObjClassGitHubUser.GITHUB_USER_ATTR_ID, "Test123");
		getConnection().modify("uid=testGitHubUserAllSetMultiTeam,dc=example,dc=com", modificationTeam, modificationUser);
		
		InOrder inOrder = inOrder(connector);
		inOrder.verify(connector).addUser("Test123", "members", "fix-trondheim");
		inOrder.verify(connector).removeUser("FIX-TestUser123", "members", "fix-trondheim");
		inOrder.verify(connector).removeUser("FIX-TestUser123", "alumni", "fix-trondheim");
		verifyNoMoreInteractions(connector);
	
		closeConnection();
	}

	@Test
	@ApplyLdifFiles({ "test-data.ldif" })
	public void testModifyGitHubOrgAllSetMultiTeam() throws LdapException, IOException {
		GitHubConnector connector = Mockito.mock(GitHubConnector.class);
		interceptor.setGitHubConnector(connector);

		DefaultModification modification = new DefaultModification(ModificationOperation.REPLACE_ATTRIBUTE, ObjClassGitHubUser.GITHUB_ORG_ATTR_ID, "some-other-org");
		getConnection().modify("uid=testGitHubUserAllSetMultiTeam,dc=example,dc=com", modification);
		
		InOrder inOrder = inOrder(connector);
		inOrder.verify(connector).addUser("FIX-TestUser123", "members", "some-other-org");
		inOrder.verify(connector).addUser("FIX-TestUser123", "alumni", "some-other-org");
		inOrder.verify(connector).removeUser("FIX-TestUser123", "members", "fix-trondheim");
		inOrder.verify(connector).removeUser("FIX-TestUser123", "alumni", "fix-trondheim");
		verifyNoMoreInteractions(connector);

		closeConnection();
	}
	
}
