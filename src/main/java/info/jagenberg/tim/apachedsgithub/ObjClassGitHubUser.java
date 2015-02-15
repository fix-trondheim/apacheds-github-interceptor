package info.jagenberg.tim.apachedsgithub;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.directory.api.ldap.model.entry.Attribute;
import org.apache.directory.api.ldap.model.entry.Entry;
import org.apache.directory.api.ldap.model.entry.Value;
import org.apache.directory.api.ldap.model.exception.LdapInvalidAttributeValueException;
import org.apache.directory.api.ldap.model.schema.AttributeType;

public class ObjClassGitHubUser {

	public static final String OBJCLASS_ATTR_ID = "2.5.4.0";
	public static final String GITHUB_USER_ATTR_ID = "2.25.338967291031856023576548935457224115483.1.1";
	public static final String GITHUB_TEAM_ATTR_ID = "2.25.338967291031856023576548935457224115483.1.3";
	public static final String GITHUB_ORG_ATTR_ID = "2.25.338967291031856023576548935457224115483.1.2";
	public static Map<String, String> attrLiterals;

	static {
		Map<String, String> map = new HashMap<>();
		map.put(OBJCLASS_ATTR_ID, "githubUser");
		map.put(GITHUB_USER_ATTR_ID, "githubUserName");
		map.put(GITHUB_TEAM_ATTR_ID, "githubTeamName");
		map.put(GITHUB_ORG_ATTR_ID, "githubOrganizationName");
		attrLiterals = Collections.unmodifiableMap(map);
	}

	private ObjClassGitHubUser() {
	}

	public static boolean isObjectClassGitHubUser(Entry entry) {
		return entry.contains(OBJCLASS_ATTR_ID, attrLiterals.get(OBJCLASS_ATTR_ID));
	}

	public static boolean hasAllGitHubAttributesSet(Entry entry) {
		boolean allSet = true;
		try {
			Attribute orgAttr = entry.get(new AttributeType(GITHUB_ORG_ATTR_ID));
			Attribute teamAttr = entry.get(new AttributeType(GITHUB_TEAM_ATTR_ID));
			Attribute userAttr = entry.get(new AttributeType(GITHUB_USER_ATTR_ID));
			allSet &= orgAttr != null;
			allSet &= teamAttr != null;
			allSet &= userAttr != null;
			if (allSet) {
				String orgVal = orgAttr.getString();
				String teamVal = teamAttr.getString();
				String userVal = userAttr.getString();
				allSet &= orgVal != null && !orgVal.isEmpty();
				allSet &= teamVal != null && !teamVal.isEmpty();
				allSet &= userVal != null && !userVal.isEmpty();
			}
		} catch (LdapInvalidAttributeValueException e) {
			allSet = false;
		}
		return allSet;
	}

	private static String getStringValue(Entry githubEntry, String attrID) {
		String value;
		try {
			Attribute attribute = githubEntry.get(new AttributeType(attrID));
			if (attribute == null) {
				throw new IllegalArgumentException("Attribute " + attrLiterals.get(attrID) + " is not set");
			}
			value = attribute.getString();
		} catch (LdapInvalidAttributeValueException e) {
			throw new IllegalArgumentException("Could not find attribute " + attrLiterals.get(attrID), e);
		}
		return value;
	}

	private static List<String> getStringValues(Entry githubEntry, String attrID) {
		List<String> values = new ArrayList<>();
		Attribute attribute = githubEntry.get(new AttributeType(attrID));
		if (attribute == null) {
			throw new IllegalArgumentException("Attribute " + attrLiterals.get(attrID) + " is not set");
		}
		for (Value<?> value : attribute) {
			values.add(value.getString());
		}
		return values;
	}

	public static String getUser(Entry gitHubUserEntry) {
		return getStringValue(gitHubUserEntry, GITHUB_USER_ATTR_ID);
	}

	public static List<String> getTeams(Entry gitHubUserEntry) {
		return getStringValues(gitHubUserEntry, GITHUB_TEAM_ATTR_ID);
	}

	public static String getOrg(Entry gitHubUserEntry) {
		return getStringValue(gitHubUserEntry, GITHUB_ORG_ATTR_ID);
	}

}
