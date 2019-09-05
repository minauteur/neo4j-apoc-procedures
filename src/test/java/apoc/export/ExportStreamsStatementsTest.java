package apoc.export;

import apoc.export.csv.ExportCSV;
import apoc.export.cypher.ExportCypher;
import apoc.export.json.ExportJson;
import apoc.util.TestUtil;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;
import org.neo4j.graphdb.GraphDatabaseService;
import org.neo4j.test.TestGraphDatabaseFactory;

import static org.junit.Assert.assertEquals;

public class ExportStreamsStatementsTest {

    private static GraphDatabaseService db;

    @BeforeClass
    public static void setUp() throws Exception {
        db = new TestGraphDatabaseFactory()
                .newImpermanentDatabaseBuilder()
                .newGraphDatabase();
        TestUtil.registerProcedure(db, ExportCSV.class, ExportCypher.class, ExportJson.class);
        db.execute("CREATE (f:User:Customer {name:'Foo', age:42})-[:BOUGHT]->(b:Product {name:'Apple Watch Series 4'})").close();
    }

    @AfterClass
    public static void tearDown() {
        db.shutdown();
    }

    @Test
    public void shouldStreamCSVData() {
        String expected = String.format("\"_id\",\"_labels\",\"age\",\"name\",\"_start\",\"_end\",\"_type\"%n" +
                "\"0\",\":Customer:User\",\"42\",\"Foo\",,,%n" +
                "\"1\",\":Product\",\"\",\"Apple Watch Series 4\",,,%n" +
                ",,,,\"0\",\"1\",\"BOUGHT\"%n");
        String statement = "CALL apoc.export.csv.all(null,{stream:true})";
        TestUtil.testCall(db, statement, (res) -> assertEquals(expected, res.get("data")));
    }

    @Test(expected = RuntimeException.class)
    public void shouldNotExportCSVData() {
        try {
            String statement = "CALL apoc.export.csv.all('file.csv', {})";
            TestUtil.testCall(db, statement, (res) -> {});
        } catch (RuntimeException e) {
            String expectedMessage = "Failed to invoke procedure `apoc.export.csv.all`: " +
                    "Caused by: java.lang.RuntimeException: Export to files not enabled, " +
                    "please set apoc.export.file.enabled=true in your neo4j.conf";
            assertEquals(expectedMessage, e.getMessage());
            throw e;
        }
    }

    @Test
    public void shouldStreamCypherStatements() {
        String expected = String.format(":begin%n" +
                "CREATE CONSTRAINT ON (node:`UNIQUE IMPORT LABEL`) ASSERT (node.`UNIQUE IMPORT ID`) IS UNIQUE;%n" +
                ":commit%n" +
                ":begin%n" +
                "UNWIND [{_id:1, properties:{name:\"Apple Watch Series 4\"}}] as row%n" +
                "CREATE (n:`UNIQUE IMPORT LABEL`{`UNIQUE IMPORT ID`: row._id}) SET n += row.properties SET n:Product;%n" +
                "UNWIND [{_id:0, properties:{name:\"Foo\", age:42}}] as row%n" +
                "CREATE (n:`UNIQUE IMPORT LABEL`{`UNIQUE IMPORT ID`: row._id}) SET n += row.properties SET n:User:Customer;%n" +
                ":commit%n" +
                ":begin%n" +
                "UNWIND [{start: {_id:0}, end: {_id:1}, properties:{}}] as row%n" +
                "MATCH (start:`UNIQUE IMPORT LABEL`{`UNIQUE IMPORT ID`: row.start._id})%n" +
                "MATCH (end:`UNIQUE IMPORT LABEL`{`UNIQUE IMPORT ID`: row.end._id})%n" +
                "CREATE (start)-[r:BOUGHT]->(end) SET r += row.properties;%n" +
                ":commit%n" +
                ":begin%n" +
                "MATCH (n:`UNIQUE IMPORT LABEL`)  WITH n LIMIT 20000 REMOVE n:`UNIQUE IMPORT LABEL` REMOVE n.`UNIQUE IMPORT ID`;%n" +
                ":commit%n" +
                ":begin%n" +
                "DROP CONSTRAINT ON (node:`UNIQUE IMPORT LABEL`) ASSERT (node.`UNIQUE IMPORT ID`) IS UNIQUE;%n" +
                ":commit%n");
        String statement = "CALL apoc.export.cypher.all(null,{streamStatements:true})";
        TestUtil.testCall(db, statement, (res) -> assertEquals(expected, res.get("cypherStatements")));
    }

    @Test(expected = RuntimeException.class)
    public void shouldNotExportCypherStatements() {
        try {
            String statement = "CALL apoc.export.cypher.all('file.cypher', {})";
            TestUtil.testCall(db, statement, (res) -> {});
        } catch (RuntimeException e) {
            String expectedMessage = "Failed to invoke procedure `apoc.export.cypher.all`: " +
                    "Caused by: java.lang.RuntimeException: Export to files not enabled, " +
                    "please set apoc.export.file.enabled=true in your neo4j.conf";
            assertEquals(expectedMessage, e.getMessage());
            throw e;
        }
    }

}