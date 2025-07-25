package org.infinispan.server.persistence;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import org.infinispan.client.hotrod.RemoteCache;
import org.infinispan.configuration.cache.CacheMode;
import org.infinispan.server.test.api.TestClientDriver;
import org.infinispan.server.test.core.Common;
import org.infinispan.server.test.core.persistence.Database;
import org.infinispan.server.test.junit5.InfinispanServer;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ArgumentsSource;

@org.infinispan.server.test.core.tags.Database
public class AsyncJdbcStringBasedCacheStore {

    @InfinispanServer(PersistenceIT.class)
    public static TestClientDriver SERVERS;

    @ParameterizedTest
    @ArgumentsSource(Common.DatabaseProvider.class)
    public void testPutRemove(Database database) throws Exception {
        int numEntries = 10;
        String keyPrefix = "testPutRemove-k-";
        String valuePrefix = "testPutRemove-k-";

        JdbcConfigurationUtil jdbcUtil = new JdbcConfigurationUtil(CacheMode.REPL_SYNC, database, false, true)
                .setLockingConfigurations();
        RemoteCache<String, String> cache = SERVERS.hotrod()
                .withClientConfiguration(database.getHotrodClientProperties())
                .withServerConfiguration(jdbcUtil.getConfigurationBuilder()).create();
        try(TableManipulation table = new TableManipulation(cache.getName(), jdbcUtil.getPersistenceConfiguration())) {
            // test PUT operation
            for (int i = 0; i < numEntries; i++) {
                cache.putAsync(keyPrefix+i, valuePrefix+i).toCompletableFuture().get();
            }

            assertCountRow(table.countAllRows(), numEntries);
            for (int i = 0; i < numEntries; i++) {
                String key = keyPrefix + i;
                String keyFromDatabase = table.getValueByKey(key);
                assertNotNull("Key " + key + " was not found in DB" , keyFromDatabase);
            }

            //test REMOVE operation
            for (int i = 0; i < numEntries; i++) {
                cache.removeAsync(keyPrefix + i).toCompletableFuture().get();
            }
            assertCountRow(table.countAllRows(), 0);
        }

    }

    public void assertCountRow(int result, int numberExpected) {
        assertEquals(numberExpected, result);
    }
}
