package info.jagenberg.tim.apachedsgithub;

import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import java.io.IOException;
import java.net.URL;
import java.util.Collection;
import java.util.List;
import java.util.Map;

import org.apache.directory.api.ldap.model.entry.Attribute;
import org.apache.directory.api.ldap.model.entry.DefaultModification;
import org.apache.directory.api.ldap.model.entry.Modification;
import org.apache.directory.api.ldap.model.entry.ModificationOperation;
import org.apache.directory.api.ldap.model.exception.LdapException;
import org.apache.directory.api.ldap.model.ldif.LdifEntry;
import org.apache.directory.api.ldap.model.ldif.LdifReader;
import org.apache.directory.api.ldap.model.message.AbandonListener;
import org.apache.directory.api.ldap.model.message.AbandonableRequest;
import org.apache.directory.api.ldap.model.message.Control;
import org.apache.directory.api.ldap.model.message.MessageTypeEnum;
import org.apache.directory.api.ldap.model.message.ModifyRequest;
import org.apache.directory.api.ldap.model.message.ResultResponse;
import org.apache.directory.api.ldap.model.name.Dn;
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
import org.mockito.Mockito;

@RunWith(FrameworkRunner.class)
@CreateDS(partitions = { @CreatePartition(name = "example", suffix = "dc=example,dc=com", contextEntry = @ContextEntry(entryLdif = "dn: dc=example,dc=com\ndc: example\nobjectClass: top\nobjectClass: domain\n\n")) })
@CreateLdapServer(transports = { @CreateTransport(protocol = "LDAP") })
@ApplyLdifFiles({ "github.ldif" })
public class GithubInterceptorTest extends AbstractLdapTestUnit {

	GitHubConnector connector = Mockito.mock(GitHubConnector.class);
	private LdapConnection connection = null;

	@Before
	public void setUp() throws Exception {
		List<Interceptor> interceptors = getService().getInterceptors();
		GithubInterceptor interceptor = new GithubInterceptor(connector);
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
	public void testAddGitHubUserAllSet() throws LdapException, IOException {
		URL testFile = getClass().getResource("/testGitHubUserAllSet.ldif");

		try (LdifReader reader = new LdifReader(testFile.getPath())) {
			LdifEntry entry = reader.next();
			getConnection().add(entry.getEntry());
		}

		verify(connector, times(1)).addUser("FIX-TestUser123", "members", "fix-trondheim");

		closeConnection();
	}

	@Test
	public void testAddGitHubUserAllSetMultiTeam() throws LdapException, IOException {
		URL testFile = getClass().getResource("/testGitHubUserAllSetMultiTeam.ldif");

		try (LdifReader reader = new LdifReader(testFile.getPath())) {
			LdifEntry entry = reader.next();
			getConnection().add(entry.getEntry());
		}

		verify(connector, times(1)).addUser("FIX-TestUser123", "members", "fix-trondheim");
		verify(connector, times(1)).addUser("FIX-TestUser123", "alumni", "fix-trondheim");

		closeConnection();
	}

	@Test
	public void testAddGitHubUserUnSet() throws LdapException, IOException {
		URL testFile = getClass().getResource("/testGitHubUserUnSet.ldif");

		try (LdifReader reader = new LdifReader(testFile.getPath())) {
			LdifEntry entry = reader.next();
			getConnection().add(entry.getEntry());
		}

		verify(connector, times(0)).addUser("", "", "");

		closeConnection();
	}

	@Test
	public void testAddUser() throws LdapException, IOException {
		URL testFile = getClass().getResource("/testUser.ldif");

		try (LdifReader reader = new LdifReader(testFile.getPath())) {
			LdifEntry entry = reader.next();
			getConnection().add(entry.getEntry());
		}

		verify(connector, times(0)).addUser("", "", "");

		closeConnection();
	}

	@Test
	@ApplyLdifFiles({ "test-data.ldif" })
	public void testRemoveGitHubUserAllSet() throws LdapException, IOException {
		getConnection().delete("uid=testGitHubUserAllSet,dc=example,dc=com");

		verify(connector, times(1)).removeUser("FIX-TestUser123", "members", "fix-trondheim");

		closeConnection();
	}

	@Test
	@ApplyLdifFiles({ "test-data.ldif" })
	public void testRemoveGitHubUserAllSetMultiTeam() throws LdapException, IOException {
		getConnection().delete("uid=testGitHubUserAllSetMultiTeam,dc=example,dc=com");

		verify(connector).removeUser("FIX-TestUser123", "members", "fix-trondheim");
		verify(connector).removeUser("FIX-TestUser123", "alumni", "fix-trondheim");

		closeConnection();
	}

	@Test
	@ApplyLdifFiles({ "test-data.ldif" })
	public void testRemoveGitHubUserUnSet() throws LdapException, IOException {
		getConnection().delete("uid=testGitHubUserUnSet,dc=example,dc=com");

		verify(connector, times(0)).removeUser("", "", "");

		closeConnection();
	}

	@Test
	@ApplyLdifFiles({ "test-data.ldif" })
	public void testRemoveUser() throws LdapException, IOException {
		getConnection().delete("uid=testUser,dc=example,dc=com");

		verify(connector, times(0)).removeUser("", "", "");

		closeConnection();
	}

	@Test
	@ApplyLdifFiles({ "test-data.ldif" })
	public void testModifyGitHubUserAllSetMultiTeam() throws LdapException, IOException {
		DefaultModification modification = new DefaultModification(ModificationOperation.REPLACE_ATTRIBUTE, ObjClassGitHubUser.GITHUB_USER_ATTR_ID, "Test123");
		getConnection().modify("uid=testGitHubUserAllSetMultiTeam,dc=example,dc=com", modification);
		
		verify(connector, times(1)).removeUser("FIX-TestUser123", "members", "fix-trondheim");
		verify(connector, times(1)).removeUser("FIX-TestUser123", "alumni", "fix-trondheim");
		verify(connector, times(1)).addUser("Test123", "members", "fix-trondheim");
		verify(connector, times(1)).addUser("Test123", "alumni", "fix-trondheim");

		closeConnection();
	}

}
