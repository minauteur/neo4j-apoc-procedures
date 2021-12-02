package apoc.load;

import apoc.util.TestUtil;
import apoc.util.Util;
import org.junit.After;
import org.junit.Assume;
import org.junit.Before;
import org.junit.Test;
import org.neo4j.graphdb.GraphDatabaseService;
import org.neo4j.graphdb.QueryExecutionException;
import org.neo4j.graphdb.Result;
import org.neo4j.test.TestGraphDatabaseFactory;
import org.testcontainers.containers.GenericContainer;

import java.net.URL;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map;

import static apoc.util.MapUtil.map;
import static apoc.util.TestUtil.getUrlFileName;
import static apoc.util.TestUtil.testResult;
import static java.util.Arrays.asList;
import static org.junit.Assert.*;

public class LoadCsvTest {

    private GraphDatabaseService db;

    private GenericContainer httpServer;

    @Before public void setUp() throws Exception {
        db = new TestGraphDatabaseFactory().newImpermanentDatabaseBuilder()
                .setConfig("apoc.import.file.enabled","true")
                .setConfig("dbms.directories.import", Paths.get(TestUtil.getUrlFileName("test.csv").toURI()).getParent().toString())
                .newGraphDatabase();
        TestUtil.registerProcedure(db, LoadCsv.class);
    }

    @After public void tearDown() {
        db.shutdown();
    }

    @Test public void testLoadCsv() throws Exception {
        String url = "test.csv";
        testResult(db, "CALL apoc.load.csv({url},{results:['map','list','stringMap','strings']})", map("url",url.toString()), // 'file:test.csv'
                (r) -> {
                    assertRow(r,0L,"name","Selma","age","8");
                    assertRow(r,1L,"name","Rana","age","11");
                    assertRow(r,2L,"name","Selina","age","18");
                    assertEquals(false, r.hasNext());
                });
    }

    /*
    WITH 'file:///test.csv' AS url
CALL apoc.load.csv(url,) YIELD map AS m
RETURN m.col_1,m.col_2,m.col_3
     */
    @Test public void testLoadCsvWithEmptyColumns() throws Exception {
        String url = "empty_columns.csv";
        testResult(db, "CALL apoc.load.csv({url},{failOnError:false,mapping:{col_2:{type:'int'}}})", map("url",url.toString()), // 'file:test.csv'
                (r) -> {
                    Map<String, Object> row = r.next();
                    assertEquals(map("col_1", "1","col_2", null,"col_3", "1"), row.get("map"));
                    row = r.next();
                    assertEquals(map("col_1", "2","col_2", 2L,"col_3", ""), row.get("map"));
                    row = r.next();
                    assertEquals(map("col_1", "3","col_2", 3L,"col_3", "3"), row.get("map"));
                    assertEquals(false, r.hasNext());
                });
        testResult(db, "CALL apoc.load.csv({url},{failOnError:false,nullValues:[''], mapping:{col_1:{type:'int'}}})", map("url",url.toString()), // 'file:test.csv'
                (r) -> {
                    Map<String, Object> row = r.next();
                    assertEquals(map("col_1", 1L,"col_2", null,"col_3", "1"), row.get("map"));
                    row = r.next();
                    assertEquals(map("col_1", 2L,"col_2", "2","col_3", null), row.get("map"));
                    row = r.next();
                    assertEquals(map("col_1", 3L,"col_2", "3","col_3", "3"), row.get("map"));
                    assertEquals(false, r.hasNext());
                });
        testResult(db, "CALL apoc.load.csv({url},{failOnError:false,mapping:{col_3:{type:'int',nullValues:['']}}})", map("url",url.toString()), // 'file:test.csv'
                (r) -> {
                    Map<String, Object> row = r.next();
                    assertEquals(map("col_1", "1","col_2", "","col_3", 1L), row.get("map"));
                    row = r.next();
                    assertEquals(map("col_1", "2","col_2", "2","col_3", null), row.get("map"));
                    row = r.next();
                    assertEquals(map("col_1", "3","col_2", "3","col_3", 3L), row.get("map"));
                    assertEquals(false, r.hasNext());
                });
    }

    static void assertRow(Result r, long lineNo, Object...data) {
        Map<String, Object> row = r.next();
        Map<String, Object> map = map(data);
        assertEquals(map, row.get("map"));
        Map<Object, Object> stringMap = new LinkedHashMap<>(map.size());
        map.forEach((k,v) -> stringMap.put(k,v == null ? null : v.toString()));
        assertEquals(stringMap, row.get("stringMap"));
        assertEquals(new ArrayList<>(map.values()), row.get("list"));
        assertEquals(new ArrayList<>(stringMap.values()), row.get("strings"));
        assertEquals(lineNo, row.get("lineNo"));
    }
    static void assertRow(Result r, String name, String age, long lineNo) {
        Map<String, Object> row = r.next();
        assertEquals(map("name", name,"age", age), row.get("map"));
        assertEquals(asList(name, age), row.get("list"));
        assertEquals(lineNo, row.get("lineNo"));
    }

    @Test public void testLoadCsvSkip() throws Exception {
        String url = "test.csv";
        testResult(db, "CALL apoc.load.csv({url},{skip:1,limit:1,results:['map','list','stringMap','strings']})", map("url",url.toString()), // 'file:test.csv'
                (r) -> {
                    assertRow(r, "Rana", "11", 1L);
                    assertEquals(false, r.hasNext());
                });
    }
    @Test public void testLoadCsvTabSeparator() throws Exception {
        String url = "test-tab.csv";
        testResult(db, "CALL apoc.load.csv({url},{sep:'TAB',results:['map','list','stringMap','strings']})", map("url",url.toString()), // 'file:test.csv'
                (r) -> {
                    assertRow(r, 0L,"name", "Rana", "age","11");
                    assertEquals(false, r.hasNext());
                });
    }

    @Test public void testLoadCsvNoHeader() throws Exception {
        String url = "test-no-header.csv";
        testResult(db, "CALL apoc.load.csv({url},{header:false,results:['map','list','stringMap','strings']})", map("url",url.toString()), // 'file:test.csv'
                (r) -> {
                    Map<String, Object> row = r.next();
                    assertEquals(null, row.get("map"));
                    assertEquals(asList("Selma", "8"), row.get("list"));
                    assertEquals(0L, row.get("lineNo"));
                    assertEquals(false, r.hasNext());
                });
    }
    @Test public void testLoadCsvIgnoreFields() throws Exception {
        String url = "test-tab.csv";
        testResult(db, "CALL apoc.load.csv({url},{ignore:['age'],sep:'TAB',results:['map','list','stringMap','strings']})", map("url",url.toString()), // 'file:test.csv'
                (r) -> {
                    assertRow(r,0L,"name","Rana");
                    assertEquals(false, r.hasNext());
                });
    }

    @Test public void testLoadCsvColonSeparator() throws Exception {
        String url = "test.dsv";
        testResult(db, "CALL apoc.load.csv({url},{sep:':',results:['map','list','stringMap','strings']})", map("url",url.toString()), // 'file:test.csv'
                (r) -> {
                    assertRow(r,0L,"name","Rana","age","11");
                    assertFalse(r.hasNext());
                });
    }

    @Test public void testPipeArraySeparator() throws Exception {
        String url = "test-pipe-column.csv";
        testResult(db, "CALL apoc.load.csv({url},{results:['map','list','stringMap','strings'],mapping:{name:{type:'string'},beverage:{array:true,arraySep:'|',type:'string'}}})", map("url",url.toString()), // 'file:test.csv'
                (r) -> {
                    assertEquals(asList("Selma", asList("Soda")), r.next().get("list"));
                    assertEquals(asList("Rana", asList("Tea", "Milk")), r.next().get("list"));
                    assertEquals(asList("Selina", asList("Cola")), r.next().get("list"));
                });
    }

    @Test public void testMapping() throws Exception {
        String url = "test-mapping.csv";
        testResult(db, "CALL apoc.load.csv({url},{results:['map','list','stringMap','strings'],mapping:{name:{type:'string'},age:{type:'int'},kids:{array:true,arraySep:':',type:'int'},pass:{ignore:true}}})", map("url",url.toString()), // 'file:test.csv'
                (r) -> {
                    Map<String, Object> row = r.next();
                    assertEquals(map("name", "Michael", "age", 41L, "kids", asList(8L, 11L, 18L)), row.get("map"));
                    assertEquals(map("name", "Michael", "age", "41", "kids", "8:11:18"), row.get("stringMap"));
                    assertEquals(asList("Michael", 41L, asList(8L, 11L, 18L)), row.get("list"));
                    assertEquals(asList("Michael", "41", "8:11:18"), row.get("strings"));
                    assertEquals(0L, row.get("lineNo"));
                    assertEquals(false, r.hasNext());
                });
    }

    @Test
    public void testLoadCsvByUrl() throws Exception {
        URL url = new URL("https://raw.githubusercontent.com/neo4j-contrib/neo4j-apoc-procedures/3.1/src/test/resources/test.csv");
        testResult(db, "CALL apoc.load.csv({url},{results:['map','list','stringMap','strings']})", map("url", url.toString()),
                (r) -> {
                    assertRow(r,0L,"name","Selma","age","8");
                    assertRow(r,1L,"name","Rana","age","11");
                    assertRow(r,2L,"name","Selina","age","18");
                    assertEquals(false, r.hasNext());
                });

    }

    @Test
    public void testLoadCsvByUrlRedirect() throws Exception {
        URL url = new URL("http://bit.ly/2nXgHA2");
        testResult(db, "CALL apoc.load.csv({url},{results:['map','list','stringMap','strings']})", map("url", url.toString()),
                (r) -> {
                    assertRow(r,0L,"name","Selma","age","8");
                    assertRow(r,1L,"name","Rana","age","11");
                    assertRow(r,2L,"name","Selina","age","18");
                    assertEquals(false, r.hasNext());
                });
    }

    @Test
    public void testLoadCsvNoFailOnError() throws Exception {
        String url = getUrlFileName("test.csv").getPath();
        testResult(db, "CALL apoc.load.csv({url},{failOnError:false})", map("url",url), // 'file:test.csv'
                (r) -> {
                    Map<String, Object> row = r.next();
                    assertEquals(0L, row.get("lineNo"));
                    assertEquals(asList("Selma","8"), row.get("list"));
                    assertEquals(Util.map("name","Selma","age","8"), row.get("map"));
                    assertEquals(true, r.hasNext());
                    row = r.next();
                    assertEquals(1L, row.get("lineNo"));
                    assertEquals(asList("Rana","11"), row.get("list"));
                    assertEquals(Util.map("name","Rana","age","11"), row.get("map"));
                    assertEquals(true, r.hasNext());
                    row = r.next();
                    assertEquals(2L, row.get("lineNo"));
                    assertEquals(asList("Selina","18"), row.get("list"));
                    assertEquals(Util.map("name","Selina","age","18"), row.get("map"));
                    assertEquals(false, r.hasNext());
                });
    }

    @Test public void testLoadCsvZip() throws Exception {
        String url = "testload.zip";
        testResult(db, "CALL apoc.load.csv({url},{results:['map','list','stringMap','strings']})", map("url",url.toString()+"!csv/test.csv"), // 'file:test.csv'
                (r) -> {
                    assertRow(r,0L,"name","Selma","age","8");
                    assertRow(r,1L,"name","Rana","age","11");
                    assertRow(r,2L,"name","Selina","age","18");
                    assertEquals(false, r.hasNext());
                });
    }

    @Test public void testLoadCsvTar() throws Exception {
        String url = "testload.tar";
        testResult(db, "CALL apoc.load.csv({url},{results:['map','list','stringMap','strings']})", map("url",url.toString()+"!csv/test.csv"), // 'file:test.csv'
                (r) -> {
                    assertRow(r,0L,"name","Selma","age","8");
                    assertRow(r,1L,"name","Rana","age","11");
                    assertRow(r,2L,"name","Selina","age","18");
                    assertEquals(false, r.hasNext());
                });
    }

    @Test public void testLoadCsvTarGz() throws Exception {
        String url = "testload.tar.gz";
        testResult(db, "CALL apoc.load.csv({url},{results:['map','list','stringMap','strings']})", map("url",url.toString()+"!csv/test.csv"), // 'file:test.csv'
                (r) -> {
                    assertRow(r,0L,"name","Selma","age","8");
                    assertRow(r,1L,"name","Rana","age","11");
                    assertRow(r,2L,"name","Selina","age","18");
                    assertEquals(false, r.hasNext());
                });
    }

    @Test public void testLoadCsvTgz() throws Exception {
        String url = "testload.tgz";
        testResult(db, "CALL apoc.load.csv({url},{results:['map','list','stringMap','strings']})", map("url",url.toString()+"!csv/test.csv"), // 'file:test.csv'
                (r) -> {
                    assertRow(r,0L,"name","Selma","age","8");
                    assertRow(r,1L,"name","Rana","age","11");
                    assertRow(r,2L,"name","Selina","age","18");
                    assertEquals(false, r.hasNext());
                });
    }

    @Test public void testLoadCsvZipByUrl() throws Exception {
        URL url = new URL("https://github.com/neo4j-contrib/neo4j-apoc-procedures/blob/3.4/src/test/resources/testload.zip?raw=true");
        testResult(db, "CALL apoc.load.csv({url},{results:['map','list','stringMap','strings']})", map("url",url.toString()+"!csv/test.csv"), // 'file:test.csv'
                (r) -> {
                    assertRow(r,0L,"name","Selma","age","8");
                    assertRow(r,1L,"name","Rana","age","11");
                    assertRow(r,2L,"name","Selina","age","18");
                    assertEquals(false, r.hasNext());
                });
    }

    @Test public void testLoadCsvTarByUrl() throws Exception {
        URL url = new URL("https://github.com/neo4j-contrib/neo4j-apoc-procedures/blob/3.4/src/test/resources/testload.tar?raw=true");
        testResult(db, "CALL apoc.load.csv({url},{results:['map','list','stringMap','strings']})", map("url",url.toString()+"!csv/test.csv"), // 'file:test.csv'
                (r) -> {
                    assertRow(r,0L,"name","Selma","age","8");
                    assertRow(r,1L,"name","Rana","age","11");
                    assertRow(r,2L,"name","Selina","age","18");
                    assertEquals(false, r.hasNext());
                });
    }

    @Test public void testLoadCsvTarGzByUrl() throws Exception {
        URL url = new URL("https://github.com/neo4j-contrib/neo4j-apoc-procedures/blob/3.4/src/test/resources/testload.tar.gz?raw=true");
        testResult(db, "CALL apoc.load.csv({url},{results:['map','list','stringMap','strings']})", map("url",url.toString()+"!csv/test.csv"), // 'file:test.csv'
                (r) -> {
                    assertRow(r,0L,"name","Selma","age","8");
                    assertRow(r,1L,"name","Rana","age","11");
                    assertRow(r,2L,"name","Selina","age","18");
                    assertEquals(false, r.hasNext());
                });
    }

    @Test public void testLoadCsvTgzByUrl() throws Exception {
        URL url = new URL("https://github.com/neo4j-contrib/neo4j-apoc-procedures/blob/3.4/src/test/resources/testload.tgz?raw=true");
        testResult(db, "CALL apoc.load.csv({url},{results:['map','list','stringMap','strings']})", map("url",url.toString()+"!csv/test.csv"), // 'file:test.csv'
                (r) -> {
                    assertRow(r,0L,"name","Selma","age","8");
                    assertRow(r,1L,"name","Rana","age","11");
                    assertRow(r,2L,"name","Selina","age","18");
                    assertEquals(false, r.hasNext());
                });
    }

    @Test(expected = QueryExecutionException.class)
    public void testLoadRedirectWithProtocolChange() {
        TestUtil.ignoreException(() -> {
            httpServer = new GenericContainer("alpine")
                    .withCommand("/bin/sh", "-c", "while true; do { echo -e 'HTTP/1.1 301 Moved Permanently\\r\\nLocation: file:/etc/passwd'; echo ; } | nc -l -p 8000; done")
                    .withExposedPorts(8000);
            httpServer.start();
        }, Exception.class);
        Assume.assumeNotNull(httpServer);
        Assume.assumeTrue(httpServer.isRunning());
        String url = String.format("http://%s:%s", httpServer.getContainerIpAddress(), httpServer.getMappedPort(8000));
        try {
            testResult(db, "CALL apoc.load.csv($url)", map("url", url),
                    (r) -> r.hasNext());
        } catch (QueryExecutionException e) {
            assertTrue(e.getMessage().contains("The redirect URI has a different protocol: file:/etc/passwd"));
            throw e;
        } finally {
            httpServer.stop();
        }
    }

    @Test public void testWithEmptyQuoteChar() throws Exception {
        Assume.assumeFalse("skip this on travis it downloads 7.3 MB of data", TestUtil.isTravis());
        URL url = new URL("https://www.fhwa.dot.gov/bridge/nbi/2010/delimited/AL10.txt");
        testResult(db, "CALL apoc.load.csv({url}, {quoteChar: '\0'})", map("url",url.toString()),
                (r) -> assertEquals(16018L, r.stream().count()));
    }
}
