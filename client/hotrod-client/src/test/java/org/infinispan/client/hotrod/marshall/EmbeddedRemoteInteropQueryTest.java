package org.infinispan.client.hotrod.marshall;

import static org.infinispan.client.hotrod.test.HotRodClientTestingUtil.killRemoteCacheManager;
import static org.infinispan.client.hotrod.test.HotRodClientTestingUtil.killServers;
import static org.infinispan.configuration.cache.IndexStorage.LOCAL_HEAP;
import static org.infinispan.server.hotrod.test.HotRodTestingUtil.hotRodCacheConfiguration;
import static org.testng.AssertJUnit.assertEquals;
import static org.testng.AssertJUnit.assertNotNull;
import static org.testng.AssertJUnit.assertTrue;

import java.util.Date;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;

import org.infinispan.Cache;
import org.infinispan.client.hotrod.RemoteCache;
import org.infinispan.client.hotrod.RemoteCacheManager;
import org.infinispan.client.hotrod.configuration.ConfigurationBuilder;
import org.infinispan.client.hotrod.query.testdomain.protobuf.AccountPB;
import org.infinispan.client.hotrod.query.testdomain.protobuf.UserPB;
import org.infinispan.client.hotrod.query.testdomain.protobuf.marshallers.TestDomainSCI;
import org.infinispan.client.hotrod.test.HotRodClientTestingUtil;
import org.infinispan.commons.api.query.Query;
import org.infinispan.commons.dataconversion.MediaType;
import org.infinispan.commons.util.CloseableIterator;
import org.infinispan.configuration.global.GlobalConfigurationBuilder;
import org.infinispan.filter.AbstractKeyValueFilterConverter;
import org.infinispan.filter.KeyValueFilterConverterFactory;
import org.infinispan.manager.EmbeddedCacheManager;
import org.infinispan.metadata.Metadata;
import org.infinispan.protostream.SerializationContextInitializer;
import org.infinispan.protostream.annotations.ProtoSchema;
import org.infinispan.query.dsl.embedded.testdomain.Account;
import org.infinispan.query.dsl.embedded.testdomain.NotIndexed;
import org.infinispan.query.dsl.embedded.testdomain.Transaction;
import org.infinispan.query.dsl.embedded.testdomain.User;
import org.infinispan.query.dsl.embedded.testdomain.hsearch.AccountHS;
import org.infinispan.query.dsl.embedded.testdomain.hsearch.AddressHS;
import org.infinispan.query.dsl.embedded.testdomain.hsearch.LimitsHS;
import org.infinispan.query.dsl.embedded.testdomain.hsearch.TransactionHS;
import org.infinispan.query.dsl.embedded.testdomain.hsearch.UserHS;
import org.infinispan.server.hotrod.HotRodServer;
import org.infinispan.test.SingleCacheManagerTest;
import org.infinispan.test.fwk.CleanupAfterMethod;
import org.infinispan.test.fwk.TestCacheManagerFactory;
import org.testng.annotations.Test;

/**
 * Tests interoperability between remote query and embedded mode.
 *
 * @author anistor@redhat.com
 * @since 6.0
 */
@Test(testName = "client.hotrod.marshall.EmbeddedRemoteInteropQueryTest", groups = "functional")
@CleanupAfterMethod
public class EmbeddedRemoteInteropQueryTest extends SingleCacheManagerTest {

   protected RemoteCache<Integer, Account> remoteCache;

   private HotRodServer hotRodServer;
   private RemoteCacheManager remoteCacheManager;
   private Cache<?, ?> embeddedCache;

   @Override
   protected EmbeddedCacheManager createCacheManager() throws Exception {
      org.infinispan.configuration.cache.ConfigurationBuilder builder = createConfigBuilder();
      GlobalConfigurationBuilder globalBuilder = new GlobalConfigurationBuilder().nonClusteredDefault();
      globalBuilder.serialization().addContextInitializers(new ServerSCIImpl());

      cacheManager = TestCacheManagerFactory.createServerModeCacheManager(globalBuilder, builder);
      cache = cacheManager.getCache();

      embeddedCache = cache.getAdvancedCache();

      hotRodServer = HotRodClientTestingUtil.startHotRodServer(cacheManager);

      ConfigurationBuilder clientBuilder = HotRodClientTestingUtil.newRemoteConfigurationBuilder();
      clientBuilder.addServer().host("127.0.0.1").port(hotRodServer.getPort())
            .addContextInitializers(TestDomainSCI.INSTANCE, NotIndexedSchema.INSTANCE);

      remoteCacheManager = new RemoteCacheManager(clientBuilder.build());
      remoteCache = remoteCacheManager.getCache();
      return cacheManager;
   }

   protected org.infinispan.configuration.cache.ConfigurationBuilder createConfigBuilder() {
      org.infinispan.configuration.cache.ConfigurationBuilder builder = hotRodCacheConfiguration();
      builder.encoding().key().mediaType(MediaType.APPLICATION_OBJECT_TYPE);
      builder.encoding().value().mediaType(MediaType.APPLICATION_OBJECT_TYPE);
      builder.indexing().enable()
            .storage(LOCAL_HEAP)
            .addIndexedEntities(UserHS.class, AccountHS.class, TransactionHS.class);
      return builder;
   }

   @Override
   protected void teardown() {
      killRemoteCacheManager(remoteCacheManager);
      remoteCacheManager = null;
      killServers(hotRodServer);
      hotRodServer = null;
      super.teardown();
   }

   public void testPutAndGet() {
      Account account = createAccountPB(1);
      remoteCache.put(1, account);

      // try to get the object through the local cache interface and check it's the same object we put
      assertEquals(1, embeddedCache.keySet().size());
      Object key = embeddedCache.keySet().iterator().next();
      Object localObject = embeddedCache.get(key);
      assertAccount((Account) localObject, AccountHS.class);

      // get the object through the remote cache interface and check it's the same object we put
      Account fromRemoteCache = remoteCache.get(1);
      assertAccount(fromRemoteCache, AccountPB.class);
   }

   public void testPutAndGetForEmbeddedEntry() {
      AccountHS account = new AccountHS();
      account.setId(1);
      account.setDescription("test description");
      account.setCreationDate(new Date(42));
      cache.put(1, account);

      // try to get the object through the remote cache interface and check it's the same object we put
      assertEquals(1, remoteCache.keySet().size());
      Map.Entry<Integer, Account> entry = remoteCache.entrySet().iterator().next();
      assertAccount(entry.getValue(), AccountPB.class);

      // get the object through the embedded cache interface and check it's the same object we put
      Account fromEmbeddedCache = (Account) embeddedCache.get(1);
      assertAccount(fromEmbeddedCache, AccountHS.class);
   }

   public void testRemoteQuery() {
      Account account = createAccountPB(1);
      remoteCache.put(1, account);

      // get account back from remote cache via query and check its attributes
      Query<Account> query = remoteCache.query("FROM sample_bank_account.Account WHERE description LIKE '%test%'");
      List<Account> list = query.execute().list();

      assertNotNull(list);
      assertEquals(1, list.size());
      assertAccount(list.get(0), AccountPB.class);
   }

   public void testRemoteQueryForEmbeddedEntry() {
      AccountHS account = new AccountHS();
      account.setId(1);
      account.setDescription("test description");
      account.setCreationDate(new Date(42));
      cache.put(1, account);

      // get account back from remote cache via query and check its attributes
      Query<Account> query = remoteCache.query("FROM sample_bank_account.Account WHERE description LIKE '%test%'");
      List<Account> list = query.execute().list();

      assertNotNull(list);
      assertEquals(1, list.size());
      assertAccount(list.get(0), AccountPB.class);
   }

   public void testRemoteQueryForEmbeddedEntryOnNonIndexedField() {
      UserHS user = new UserHS();
      user.setId(1);
      user.setName("test name");
      user.setSurname("test surname");
      user.setGender(User.Gender.MALE);
      user.setNotes("1234567890");
      cache.put(1, user);

      // get user back from remote cache via query and check its attributes
      Query<User> query = remoteCache.query("FROM sample_bank_account.User WHERE notes LIKE '%567%'");
      List<User> list = query.execute().list();

      assertNotNull(list);
      assertEquals(1, list.size());
      assertNotNull(list.get(0));
      assertEquals(UserPB.class, list.get(0).getClass());
      assertEquals(1, list.get(0).getId());
      assertEquals("1234567890", list.get(0).getNotes());
   }

   public void testRemoteQueryForEmbeddedEntryOnNonIndexedType() {
      cache.put(1, new NotIndexed("testing 123"));

      // get user back from remote cache via query and check its attributes
      Query<NotIndexed> query = remoteCache.query("FROM sample_bank_account.NotIndexed WHERE notIndexedField LIKE '%123%'");
      List<NotIndexed> list = query.execute().list();

      assertNotNull(list);
      assertEquals(1, list.size());
      assertNotNull(list.get(0));
      assertEquals("testing 123", list.get(0).notIndexedField);
   }

   public void testRemoteQueryForEmbeddedEntryOnIndexedAndNonIndexedField() {
      UserHS user = new UserHS();
      user.setId(1);
      user.setName("test name");
      user.setSurname("test surname");
      user.setGender(User.Gender.MALE);
      user.setNotes("1234567890");
      cache.put(1, user);

      // get user back from remote cache via query and check its attributes
      Query<User> query = remoteCache.query("FROM sample_bank_account.User WHERE notes LIKE '%567%' AND surname = 'test surname'");
      List<User> list = query.execute().list();

      assertNotNull(list);
      assertEquals(1, list.size());
      assertNotNull(list.get(0));
      assertEquals(UserPB.class, list.get(0).getClass());
      assertEquals(1, list.get(0).getId());
      assertEquals("1234567890", list.get(0).getNotes());
      assertEquals("test surname", list.get(0).getSurname());
   }

   public void testRemoteQueryWithProjectionsForEmbeddedEntry() {
      AccountHS account = new AccountHS();
      account.setId(1);
      account.setDescription("test description");
      account.setCreationDate(new Date(42));
      cache.put(1, account);

      // get account back from remote cache via query and check its attributes
      Query<Object[]> query = remoteCache.query("SELECT description, id FROM sample_bank_account.Account WHERE description LIKE '%test%'");
      List<Object[]> list = query.execute().list();

      assertNotNull(list);
      assertEquals(1, list.size());
      assertEquals("test description", list.get(0)[0]);
      assertEquals(1, list.get(0)[1]);
   }

   public void testRemoteFullTextQuery() {
      Transaction transaction = new TransactionHS();
      transaction.setId(3);
      transaction.setDescription("Hotel");
      transaction.setLongDescription("Expenses for Infinispan F2F meeting");
      transaction.setAccountId(2);
      transaction.setAmount(99);
      transaction.setDate(new Date(42));
      transaction.setDebit(true);
      transaction.setValid(true);
      cache.put(transaction.getId(), transaction);

      // Hibernate Search 6 does not support fields that are sortable and full text at the same time
      Query<Transaction> q = remoteCache.query("from sample_bank_account.Transaction where longDescription='Expenses for Infinispan F2F meeting'");

      List<Transaction> list = q.execute().list();
      assertEquals(1, list.size());
   }

   public void testEmbeddedLuceneQuery() {
      Account account = createAccountPB(1);
      remoteCache.put(1, account);

      // get account back from local cache via query and check its attributes
      Query<Account> query = embeddedCache.query(String.format("FROM %s WHERE description LIKE '%%test%%'", AccountHS.class.getName()));
      List<Account> list = query.execute().list();

      assertNotNull(list);
      assertEquals(1, list.size());
      assertAccount(list.get(0), AccountHS.class);
   }

   public void testEmbeddedQueryForEmbeddedEntryOnNonIndexedField() {
      UserHS user = new UserHS();
      user.setId(1);
      user.setName("test name");
      user.setSurname("test surname");
      user.setGender(User.Gender.MALE);
      user.setNotes("1234567890");
      cache.put(1, user);

      // get user back from remote cache via query and check its attributes
      Query<User> query = cache.query("FROM " + UserHS.class.getName() + " WHERE notes LIKE '%567%'");
      List<User> list = query.execute().list();

      assertNotNull(list);
      assertEquals(1, list.size());
      assertNotNull(list.get(0));
      assertEquals(UserHS.class, list.get(0).getClass());
      assertEquals(1, list.get(0).getId());
      assertEquals("1234567890", list.get(0).getNotes());
   }

   public void testEmbeddedQueryForEmbeddedEntryOnNonIndexedType() {
      cache.put(1, new NotIndexed("testing 123"));

      // get user back from remote cache via query and check its attributes
      Query<NotIndexed> query = cache.query("FROM " + NotIndexed.class.getName() + " WHERE notIndexedField LIKE '%123%'");
      List<NotIndexed> list = query.execute().list();

      assertNotNull(list);
      assertEquals(1, list.size());
      assertNotNull(list.get(0));
      assertEquals("testing 123", list.get(0).notIndexedField);
   }

   public void testEmbeddedQueryForEmbeddedEntryOnIndexedAndNonIndexedField() {
      UserHS user = new UserHS();
      user.setId(1);
      user.setName("test name");
      user.setSurname("test surname");
      user.setGender(User.Gender.MALE);
      user.setNotes("1234567890");
      cache.put(1, user);

      // get user back from remote cache via query and check its attributes
      Query<User> query = cache.query("FROM " + UserHS.class.getName() + " WHERE notes LIKE '%567%' AND surname = 'test surname'");
      List<User> list = query.execute().list();

      assertNotNull(list);
      assertEquals(1, list.size());
      assertNotNull(list.get(0));
      assertEquals(UserHS.class, list.get(0).getClass());
      assertEquals(1, list.get(0).getId());
      assertEquals("1234567890", list.get(0).getNotes());
      assertEquals("test surname", list.get(0).getSurname());
   }

   public void testIterationForRemote() {
      IntStream.range(0, 10).forEach(id -> remoteCache.put(id, createAccountPB(id)));

      // Remote unfiltered iteration
      CloseableIterator<Map.Entry<Object, Object>> remoteUnfilteredIterator = remoteCache.retrieveEntries(null, null, 10);
      remoteUnfilteredIterator.forEachRemaining(e -> {
         Integer key = (Integer) e.getKey();
         AccountPB value = (AccountPB) e.getValue();
         assertTrue(key < 10);
         assertEquals((int) key, value.getId());
      });

      // Remote filtered iteration
      KeyValueFilterConverterFactory<Integer, Account, String> filterConverterFactory = () ->
            new AbstractKeyValueFilterConverter<Integer, Account, String>() {
               @Override
               public String filterAndConvert(Integer key, Account value, Metadata metadata) {
                  if (key % 2 == 0) {
                     return value.toString();
                  }
                  return null;
               }
            };

      hotRodServer.addKeyValueFilterConverterFactory("filterConverterFactory", filterConverterFactory);

      CloseableIterator<Map.Entry<Object, Object>> remoteFilteredIterator = remoteCache.retrieveEntries("filterConverterFactory", null, 10);
      remoteFilteredIterator.forEachRemaining(e -> {
         Integer key = (Integer) e.getKey();
         String value = (String) e.getValue();
         assertTrue(key < 10);
         assertEquals(createAccountHS(key).toString(), value);
      });

      // Embedded  iteration
      Cache<Integer, AccountHS> ourCache = (Cache<Integer, AccountHS>) embeddedCache;

      Iterator<Map.Entry<Integer, AccountHS>> localUnfilteredIterator = ourCache.entrySet().stream().iterator();
      localUnfilteredIterator.forEachRemaining(e -> {
         Integer key = e.getKey();
         AccountHS value = e.getValue();
         assertTrue(key < 10);
         assertEquals((int) key, value.getId());
      });
   }

   public void testEqEmptyStringRemote() {
      UserHS user = new UserHS();
      user.setId(1);
      user.setName("test name");
      user.setSurname("test surname");
      user.setGender(User.Gender.MALE);
      user.setNotes("1234567890");
      cache.put(1, user);

      Query<User> q = remoteCache.query("FROM sample_bank_account.User WHERE name = ''");

      List<User> list = q.execute().list();
      assertTrue(list.isEmpty());
   }

   public void testEqSentenceRemote() {
      AccountHS account = new AccountHS();
      account.setId(1);
      account.setDescription("John Doe's first bank account");
      account.setCreationDate(new Date(42));
      cache.put(1, account);

      Query<Account> q = remoteCache.query("FROM sample_bank_account.Account WHERE description = \"John Doe's first bank account\"");

      List<Account> list = q.execute().list();
      assertEquals(1, list.size());
      assertEquals(1, list.get(0).getId());
   }

   public void testEqEmptyStringEmbedded() {
      UserHS user = new UserHS();
      user.setId(1);
      user.setName("test name");
      user.setSurname("test surname");
      user.setGender(User.Gender.MALE);
      user.setNotes("1234567890");
      cache.put(1, user);

      Query<User> q = cache.query("FROM " + UserHS.class.getName() + " WHERE name = ''");

      List<User> list = q.execute().list();
      assertTrue(list.isEmpty());
   }

   public void testEqSentenceEmbedded() {
      AccountHS account = new AccountHS();
      account.setId(1);
      account.setDescription("John Doe's first bank account");
      account.setCreationDate(new Date(42));
      cache.put(1, account);

      Query<Account> q = cache.query("FROM " + AccountHS.class.getName() + " WHERE description = \"John Doe's first bank account\"");

      List<Account> list = q.execute().list();
      assertEquals(1, list.size());
      assertEquals(1, list.get(0).getId());
   }

   public void testDuplicateBooleanProjectionEmbedded() {
      Transaction transaction = new TransactionHS();
      transaction.setId(3);
      transaction.setDescription("Hotel");
      transaction.setAccountId(2);
      transaction.setAmount(45);
      transaction.setDate(new Date(42));
      transaction.setDebit(true);
      transaction.setValid(true);
      cache.put(transaction.getId(), transaction);

      Query<Object[]> q = cache.query("SELECT id, isDebit, isDebit FROM " + TransactionHS.class.getName() + " WHERE description = 'Hotel'");
      List<Object[]> list = q.execute().list();

      assertEquals(1, list.size());
      assertEquals(3, list.get(0).length);
      assertEquals(3, list.get(0)[0]);
      assertEquals(true, list.get(0)[1]);
      assertEquals(true, list.get(0)[2]);
   }

   public void testDuplicateBooleanProjectionRemote() {
      Transaction transaction = new TransactionHS();
      transaction.setId(3);
      transaction.setDescription("Hotel");
      transaction.setAccountId(2);
      transaction.setAmount(45);
      transaction.setDate(new Date(42));
      transaction.setDebit(true);
      transaction.setValid(true);
      cache.put(transaction.getId(), transaction);

      Query<Object[]> q = remoteCache.query("SELECT id, isDebit, isDebit FROM sample_bank_account.Transaction WHERE description = 'Hotel'");
      List<Object[]> list = q.execute().list();

      assertEquals(1, list.size());
      assertEquals(3, list.get(0).length);
      assertEquals(3, list.get(0)[0]);
      assertEquals(true, list.get(0)[1]);
      assertEquals(true, list.get(0)[2]);
   }

   private AccountPB createAccountPB(int id) {
      AccountPB account = new AccountPB();
      account.setId(id);
      account.setDescription("test description");
      account.setCreationDate(new Date(42));
      return account;
   }

   private AccountHS createAccountHS(int id) {
      AccountHS account = new AccountHS();
      account.setId(id);
      account.setDescription("test description");
      account.setCreationDate(new Date(42));
      return account;
   }

   private void assertAccount(Account account, Class<?> cls) {
      assertNotNull(account);
      assertEquals(cls, account.getClass());
      assertEquals(1, account.getId());
      assertEquals("test description", account.getDescription());
      assertEquals(42, account.getCreationDate().getTime());
   }

   @ProtoSchema(
         dependsOn = NotIndexedSchema.class,
         includeClasses = {
               AddressHS.class,
               AccountHS.class,
               Account.Currency.class,
               LimitsHS.class,
               TransactionHS.class,
               UserHS.class,
               User.Gender.class
         },
         schemaFileName = "test.client.EmbeddedRemoteInteropQueryTest.proto",
         schemaFilePath = "proto/generated",
         schemaPackageName = "sample_bank_account",
         service = false
   )
   interface ServerSCI extends SerializationContextInitializer {
   }
}
