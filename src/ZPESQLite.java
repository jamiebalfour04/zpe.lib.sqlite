import jamiebalfour.generic.BinarySearchTree;
import jamiebalfour.zpe.core.ZPEObject;
import jamiebalfour.zpe.core.ZPERuntimeEnvironment;
import jamiebalfour.zpe.core.ZPEStructure;
import jamiebalfour.zpe.interfaces.ZPEObjectNativeMethod;
import jamiebalfour.zpe.interfaces.ZPEPropertyWrapper;
import jamiebalfour.zpe.interfaces.ZPEType;
import jamiebalfour.zpe.types.*;

import java.sql.*;
import java.util.Base64;
import java.util.Collections;
import java.util.List;

public class ZPESQLite extends ZPEStructure {

  private static final long serialVersionUID = -2658403322308184479L;
  private Connection conn;
  private String lastError = "";
  public ZPESQLite(ZPERuntimeEnvironment z, ZPEPropertyWrapper parent, String name) {
    super(z, parent, name);
    addNativeMethod("open", new open_Command());
    addNativeMethod("close", new close_Command());
    addNativeMethod("is_open", new is_open_Command());
    addNativeMethod("execute", new execute_Command());
    addNativeMethod("query", new query_Command());
    addNativeMethod("begin", new begin_Command());
    addNativeMethod("commit", new commit_Command());
    addNativeMethod("rollback", new rollback_Command());
    addNativeMethod("last_insert_rowid", new last_insert_rowid_Command());
    addNativeMethod("get_last_error", new get_last_error_Command());
  }


  // ---------- helpers ----------

  private synchronized boolean ensureOpen(String path) {
    clearError();
    if (conn != null) return true;
    try {
      // jdbc:sqlite:<absolute-or-relative-path>
      conn = DriverManager.getConnection("jdbc:sqlite:" + path);
      try (Statement st = conn.createStatement()) {
        // Sensible defaults for desktop apps
        st.execute("PRAGMA busy_timeout=5000"); // ms
        st.execute("PRAGMA journal_mode=WAL");
        st.execute("PRAGMA foreign_keys=ON");
      }
      return true;
    } catch (Exception e) {
      setError(e);
      return false;
    }
  }

  private synchronized boolean isOpen() {
    try {
      return conn != null && !conn.isClosed();
    } catch (SQLException e) {
      setError(e);
      return false;
    }
  }

  private synchronized boolean closeInternal() {
    clearError();
    if (conn == null) return true;
    try {
      conn.close();
      conn = null;
      return true;
    } catch (SQLException e) {
      setError(e);
      return false;
    }
  }

  private void setError(Exception e) {
    lastError = e.getMessage() == null ? e.toString() : e.getMessage();
  }

  private void clearError() {
    lastError = "";
  }

  private PreparedStatement prepare(String sql, List<ZPEType> params) throws SQLException {
    PreparedStatement ps = conn.prepareStatement(sql);
    for (int i = 0; i < params.size(); i++) {
      Object v = Conv.toJdbc(params.get(i));
      if (v == null) ps.setObject(i + 1, null);
      else if (v instanceof Long) ps.setLong(i + 1, (Long) v);
      else if (v instanceof Integer) ps.setInt(i + 1, (Integer) v);
      else if (v instanceof Double) ps.setDouble(i + 1, (Double) v);
      else if (v instanceof Float) ps.setFloat(i + 1, (Float) v);
      else if (v instanceof Boolean) ps.setBoolean(i + 1, (Boolean) v);
      else ps.setObject(i + 1, v);
    }
    return ps;
  }

  private ZPEType rowsToZpeList(ResultSet rs) throws SQLException {
    ResultSetMetaData md = rs.getMetaData();
    int cols = md.getColumnCount();
    ZPEList outRows = new ZPEList();
    while (rs.next()) {
      ZPEMap row = new ZPEMap();
      for (int c = 1; c <= cols; c++) {
        String name = md.getColumnLabel(c);
        Object val = rs.getObject(c);
        row.put(new ZPEString(name), Conv.fromJdbc(val));
      }
      outRows.add(new ZPEMap(row));
    }
    return new ZPEList(outRows);
  }

  private static class Conv {
    static Object toJdbc(ZPEType t) {
      if (t == null) return null;
      if (t instanceof ZPEString) return t.toString();
      if (t instanceof ZPENumber) {
        double d = ((ZPENumber) t).doubleValue();
        long asLong = (long) d;
        return d;
      }
      if (t instanceof ZPEBoolean) return ((ZPEBoolean) t).getValue();
      // Fallback to string
      return t.toString();
    }

    static ZPEType fromJdbc(Object o) {
      if (o == null) return null;
      if (o instanceof String) return new ZPEString((String) o);
      if (o instanceof Integer) return new ZPENumber(((Integer) o).doubleValue());
      if (o instanceof Long) return new ZPENumber(((Long) o).doubleValue());
      if (o instanceof Double) return new ZPENumber((Double) o);
      if (o instanceof Float) return new ZPENumber(((Float) o).doubleValue());
      if (o instanceof Boolean) return new ZPEBoolean((Boolean) o);
      if (o instanceof byte[])
        return new ZPEString(Base64.getEncoder().encodeToString((byte[]) o)); // simple BLOB handling
      return new ZPEString(o.toString());
    }

    @SuppressWarnings("unchecked")
    static List<ZPEType> asZpeListOrEmpty(ZPEType t) {
      if (t instanceof ZPEList) {
        return ((ZPEList) t).subList(0, ((ZPEList) t).size()); // adjust if your API differs
      }
      return Collections.emptyList();
    }
  }

  // ---------- native methods ----------

  public class open_Command implements ZPEObjectNativeMethod {
    @Override
    public ZPEType MainMethod(BinarySearchTree<String, ZPEType> parameters, ZPEObject parent) {
      String path = parameters.get("path").toString();
      return new ZPEBoolean(ensureOpen(path));
    }

    @Override
    public String[] getParameterNames() {
      return new String[]{"path"};
    }

    @Override
    public String[] getParameterTypes() {
      return new String[]{"string"};
    }

    @Override
    public int getRequiredPermissionLevel() {
      return 3;
    }

    @Override
    public String getName() {
      return "open";
    }
  }

  public class close_Command implements ZPEObjectNativeMethod {
    @Override
    public ZPEType MainMethod(BinarySearchTree<String, ZPEType> parameters, ZPEObject parent) {
      return new ZPEBoolean(closeInternal());
    }

    @Override
    public String[] getParameterNames() {
      return new String[]{};
    }

    @Override
    public String[] getParameterTypes() {
      return new String[]{};
    }

    @Override
    public int getRequiredPermissionLevel() {
      return 3;
    }

    @Override
    public String getName() {
      return "close";
    }
  }

  public class is_open_Command implements ZPEObjectNativeMethod {
    @Override
    public ZPEType MainMethod(BinarySearchTree<String, ZPEType> parameters, ZPEObject parent) {
      return new ZPEBoolean(isOpen());
    }

    @Override
    public String[] getParameterNames() {
      return new String[]{};
    }

    @Override
    public String[] getParameterTypes() {
      return new String[]{};
    }

    @Override
    public int getRequiredPermissionLevel() {
      return 3;
    }

    @Override
    public String getName() {
      return "is_open";
    }
  }

  public class execute_Command implements ZPEObjectNativeMethod {
    @Override
    public synchronized ZPEType MainMethod(BinarySearchTree<String, ZPEType> parameters, ZPEObject parent) {
      clearError();
      String sql = parameters.get("sql").toString();
      List<ZPEType> paramsList = Conv.asZpeListOrEmpty(parameters.get("params"));
      if (!isOpen()) {
        setError(new IllegalStateException("Database not open"));
        return new ZPENumber(0);
      }
      try (PreparedStatement ps = prepare(sql, paramsList)) {
        int updated = ps.executeUpdate();
        return new ZPENumber(updated);
      } catch (Exception e) {
        setError(e);
        return new ZPENumber(0);
      }
    }

    @Override
    public String[] getParameterNames() {
      return new String[]{"sql", "params"};
    }

    @Override
    public String[] getParameterTypes() {
      return new String[]{"string", "list?"};
    } // if you support optional markers

    @Override
    public int getRequiredPermissionLevel() {
      return 3;
    }

    @Override
    public String getName() {
      return "execute";
    }
  }

  public class query_Command implements ZPEObjectNativeMethod {
    @Override
    public synchronized ZPEType MainMethod(BinarySearchTree<String, ZPEType> parameters, ZPEObject parent) {
      clearError();
      String sql = parameters.get("sql").toString();
      List<ZPEType> paramsList = Conv.asZpeListOrEmpty(parameters.get("params"));
      if (!isOpen()) {
        setError(new IllegalStateException("Database not open"));
        return new ZPEList();
      }
      try (PreparedStatement ps = prepare(sql, paramsList); ResultSet rs = ps.executeQuery()) {
        return rowsToZpeList(rs);
      } catch (Exception e) {
        setError(e);
        return new ZPEList();
      }
    }

    @Override
    public String[] getParameterNames() {
      return new String[]{"sql", "params"};
    }

    @Override
    public String[] getParameterTypes() {
      return new String[]{"string", "list?"};
    }

    @Override
    public int getRequiredPermissionLevel() {
      return 3;
    }

    @Override
    public String getName() {
      return "query";
    }
  }

  public class begin_Command implements ZPEObjectNativeMethod {
    @Override
    public synchronized ZPEType MainMethod(BinarySearchTree<String, ZPEType> parameters, ZPEObject parent) {
      clearError();
      if (!isOpen()) {
        setError(new IllegalStateException("Database not open"));
        return new ZPEBoolean(false);
      }
      try (Statement st = conn.createStatement()) {
        st.execute("BEGIN");
        return new ZPEBoolean(true);
      } catch (SQLException e) {
        setError(e);
        return new ZPEBoolean(false);
      }
    }

    @Override
    public String[] getParameterNames() {
      return new String[]{};
    }

    @Override
    public String[] getParameterTypes() {
      return new String[]{};
    }

    @Override
    public int getRequiredPermissionLevel() {
      return 3;
    }

    @Override
    public String getName() {
      return "begin";
    }
  }

  public class commit_Command implements ZPEObjectNativeMethod {
    @Override
    public synchronized ZPEType MainMethod(BinarySearchTree<String, ZPEType> parameters, ZPEObject parent) {
      clearError();
      if (!isOpen()) {
        setError(new IllegalStateException("Database not open"));
        return new ZPEBoolean(false);
      }
      try (Statement st = conn.createStatement()) {
        st.execute("COMMIT");
        return new ZPEBoolean(true);
      } catch (SQLException e) {
        setError(e);
        return new ZPEBoolean(false);
      }
    }

    @Override
    public String[] getParameterNames() {
      return new String[]{};
    }

    @Override
    public String[] getParameterTypes() {
      return new String[]{};
    }

    @Override
    public int getRequiredPermissionLevel() {
      return 3;
    }

    @Override
    public String getName() {
      return "commit";
    }
  }

  public class rollback_Command implements ZPEObjectNativeMethod {
    @Override
    public synchronized ZPEType MainMethod(BinarySearchTree<String, ZPEType> parameters, ZPEObject parent) {
      clearError();
      if (!isOpen()) {
        setError(new IllegalStateException("Database not open"));
        return new ZPEBoolean(false);
      }
      try (Statement st = conn.createStatement()) {
        st.execute("ROLLBACK");
        return new ZPEBoolean(true);
      } catch (SQLException e) {
        setError(e);
        return new ZPEBoolean(false);
      }
    }

    @Override
    public String[] getParameterNames() {
      return new String[]{};
    }

    @Override
    public String[] getParameterTypes() {
      return new String[]{};
    }

    @Override
    public int getRequiredPermissionLevel() {
      return 3;
    }

    @Override
    public String getName() {
      return "rollback";
    }
  }

  public class last_insert_rowid_Command implements ZPEObjectNativeMethod {
    @Override
    public synchronized ZPEType MainMethod(BinarySearchTree<String, ZPEType> parameters, ZPEObject parent) {
      clearError();
      if (!isOpen()) {
        setError(new IllegalStateException("Database not open"));
        return new ZPENumber(-1);
      }
      try (Statement st = conn.createStatement(); ResultSet rs = st.executeQuery("SELECT last_insert_rowid()")) {
        if (rs.next()) return new ZPENumber(rs.getLong(1));
        return new ZPENumber(-1);
      } catch (SQLException e) {
        setError(e);
        return new ZPENumber(-1);
      }
    }

    @Override
    public String[] getParameterNames() {
      return new String[]{};
    }

    @Override
    public String[] getParameterTypes() {
      return new String[]{};
    }

    @Override
    public int getRequiredPermissionLevel() {
      return 3;
    }

    @Override
    public String getName() {
      return "last_insert_rowid";
    }
  }

  public class get_last_error_Command implements ZPEObjectNativeMethod {
    @Override
    public ZPEType MainMethod(BinarySearchTree<String, ZPEType> parameters, ZPEObject parent) {
      return new ZPEString(lastError == null ? "" : lastError);
    }

    @Override
    public String[] getParameterNames() {
      return new String[]{};
    }

    @Override
    public String[] getParameterTypes() {
      return new String[]{};
    }

    @Override
    public int getRequiredPermissionLevel() {
      return 3;
    }

    @Override
    public String getName() {
      return "get_last_error";
    }
  }
}
