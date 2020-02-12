import com.actiontech.dble.backend.datasource.check.AbstractConsistencyChecker;
import com.actiontech.dble.sqlengine.SQLQueryResult;

import java.util.List;
import java.util.Map;

public class CustomizeTest extends AbstractConsistencyChecker {


    @Override
    public String[] getFetchCols() {
        return new String[]{"Checksum"};
    }

    @Override
    public String getCountSQL(String dbName, String tName) {
        return "checksum table " + tName;
    }

    @Override
    public boolean resultEquals(SQLQueryResult<List<Map<String, String>>> or, SQLQueryResult<List<Map<String, String>>> cr) {
        Map<String, String> oresult = or.getResult().get(0);
        Map<String, String> cresult = cr.getResult().get(0);
        return (oresult.get("Checksum") == null && cresult.get("Checksum") == null) ||
                (oresult.get("Checksum") != null && cresult.get("Checksum") != null &&
                        oresult.get("Checksum").equals(cresult.get("Checksum")));
    }

    @Override
    public void failResponse(List<SQLQueryResult<List<Map<String, String>>>> res) {
        String errorMsg = "Global Consistency Check fail for table :" + schema + "-" + tableName;
        System.out.println(errorMsg);
        for (SQLQueryResult<List<Map<String, String>>> r : res) {
            System.out.println("Checksum is : " + r.getResult().get(0).get("Checksum"));
        }
    }

    @Override
    public void resultResponse(List<SQLQueryResult<List<Map<String, String>>>> elist) {
        String tableId = schema + "." + tableName;

        if (elist.size() == 0) {
            System.out.println("Global Consistency Check success for table :" + schema + "-" + tableName);
        } else {
            System.out.println("Global Consistency Check fail for table :" + schema + "-" + tableName);
            StringBuilder sb = new StringBuilder("Error when check Global Consistency, Table ");
            sb.append(tableName).append(" dataNode ");
            for (SQLQueryResult<List<Map<String, String>>> r : elist) {
                System.out.println("error node is : " + r.getTableName() + "-" + r.getDataNode());
                sb.append(r.getDataNode()).append(",");
            }
            sb.setLength(sb.length() - 1);
        }
    }
}
