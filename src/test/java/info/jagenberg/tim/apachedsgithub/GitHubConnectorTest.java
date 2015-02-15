package info.jagenberg.tim.apachedsgithub;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import org.junit.Before;
import org.junit.Test;

public class GitHubConnectorTest {

	private GitHubConnector connector;

	@Before
	public void setUp() throws Exception {
		connector = new GitHubConnector();
	}

	@Test
	public void testAddAndRemoveUser() {
		try {
			connector.addUser("FIX-TestUser123", "members", "fix-trondheim");
			Thread.sleep(3000);
			assertTrue(connector.hasUser("FIX-TestUser123", "members", "fix-trondheim"));
			Thread.sleep(3000);
			connector.removeUser("FIX-TestUser123", "members", "fix-trondheim");
			Thread.sleep(3000);
			assertFalse(connector.hasUser("FIX-TestUser123", "members", "fix-trondheim"));
		} catch (IllegalArgumentException | InterruptedException e) {
			fail(e.getMessage());
		}
	}

	@Test
	public void testIllegalUserAddUser() {
		try {
			connector.addUser("regnj395jtfg34t9jg", "members", "fix-trondheim");
			fail("illegal addUser did not fail");
		} catch (IllegalArgumentException e) {
			assertTrue(e.getMessage().contains("not find user regnj395jtfg34t9jg"));
		}
	}

	@Test
	public void testIllegalTeamAddUser() {
		try {
			connector.addUser("FIX-TestUser123", "dfgj432th7gw952", "fix-trondheim");
			fail("illegal addUser did not fail");
		} catch (IllegalArgumentException e) {
			assertTrue(e.getMessage().contains("not find team dfgj432th7gw952"));
		}
	}

	@Test
	public void testIllegalOrgAddUser() {
		try {
			connector.addUser("FIX-TestUser123", "members", "fgjsdht8435tj0j2");
			fail("illegal addUser did not fail");
		} catch (IllegalArgumentException e) {
			assertTrue(e.getMessage().contains("not find organization fgjsdht8435tj0j2"));
		}
	}

	@Test
	public void testIllegalUserRemoveUser() {
		try {
			connector.removeUser("regnj395jtfg34t9jg", "members", "fix-trondheim");
			fail("illegal addUser did not fail");
		} catch (IllegalArgumentException e) {
			assertTrue(e.getMessage().contains("not find user regnj395jtfg34t9jg"));
		}
	}

	@Test
	public void testIllegalTeamRemoveUser() {
		try {
			connector.removeUser("FIX-TestUser123", "dfgj432th7gw952", "fix-trondheim");
			fail("illegal addUser did not fail");
		} catch (IllegalArgumentException e) {
			assertTrue(e.getMessage().contains("not find team dfgj432th7gw952"));
		}
	}

	@Test
	public void testIllegalOrgRemoveUser() {
		try {
			connector.removeUser("FIX-TestUser123", "members", "fgjsdht8435tj0j2");
			fail("illegal addUser did not fail");
		} catch (IllegalArgumentException e) {
			assertTrue(e.getMessage().contains("not find organization fgjsdht8435tj0j2"));
		}
	}

	@Test
	public void testHasUser() {
		assertTrue(connector.hasUser("TimJay"));
		assertFalse(connector.hasUser("dfg836th3495j0fj23405u3y8"));
	}

	@Test
	public void testHasUserInTeamAndOrg() {
		assertTrue(connector.hasUser("TimJay", "owners", "fix-trondheim"));
		assertFalse(connector.hasUser("fdgm0324tj3hyjk2349ty", "owners", "fix-trondheim"));
		assertFalse(connector.hasUser("TimJay", "gj30tj02jt2tg", "fix-trondheim"));
		assertFalse(connector.hasUser("TimJay", "owners", "fegj34jg24jt"));
	}

}
