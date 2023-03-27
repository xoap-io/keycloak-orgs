package io.phasetwo.service.model.migration;

import lombok.extern.jbosslog.JBossLog;
import liquibase.database.Database;
import liquibase.change.custom.CustomTaskChange;
import liquibase.database.jvm.JdbcConnection;
import java.sql.ResultSet;
import java.sql.PreparedStatement;
import org.keycloak.models.utils.KeycloakModelUtils;
import java.util.List;
import java.util.ArrayList;
import java.util.Map;
import java.util.HashMap;
import liquibase.resource.ResourceAccessor;
import liquibase.exception.ValidationErrors;

@JBossLog
public class DecoupleOrgRoles implements CustomTaskChange {

  @Override
  public String getConfirmationMessage() {
    return "done";
  }

  @Override
  public void setFileOpener(ResourceAccessor resourceAccessor) {
  }

  @Override
  public void setUp() {
  }

  @Override
  public ValidationErrors validate(Database database) {
    return null;
  }

  @Override
  public void execute(Database database) {
    JdbcConnection conn = (JdbcConnection)database.getConnection();
    try {
      conn.createStatement().execute("delete from ORGANIZATION_ROLE");

      ResultSet rs = conn.createStatement().executeQuery("select distinct NAME from ORGANIZATION_ROLE_TMP");
      List<String> names = new ArrayList<String>();
      while (rs.next()) {
        names.add(rs.getString("NAME"));
      }

      PreparedStatement st = conn.prepareStatement("insert into ORGANIZATION_ROLE (ID, NAME)  values (?, ?)");
      Map<String,String> idMap = new HashMap<String,String>();
      for (String name : names) {
        String id = KeycloakModelUtils.generateId();
        st.setString(1, id);
        st.setString(2, name);
        st.execute();
        idMap.put(name, id);
      }

      rs = conn.createStatement().executeQuery("select u.ID, o.NAME from USER_ORGANIZATION_ROLE_MAPPING u, ORGANIZATION_ROLE_TMP o WHERE u.ROLE_ID=o.ID");
      Map<String,String> roleMap = new HashMap<String,String>();
      while (rs.next()) {
        roleMap.put(rs.getString("NAME"), rs.getString("ID"));
      }

      st = conn.prepareStatement("update USER_ORGANIZATION_ROLE_MAPPING set ROLE_ID=? where ID=?");
      for (Map.Entry<String,String> role : roleMap.entrySet()) {
        st.setString(1, idMap.get(role.getKey()));
        st.setString(2, role.getValue());
        st.execute();
      }
    } catch (Exception e) {
      log.warn("migration error", e);
    }    
  }
  
}

