package org.infinispan.client.hotrod.marshall;

import static org.infinispan.client.hotrod.test.HotRodClientTestingUtil.killRemoteCacheManager;
import static org.infinispan.client.hotrod.test.HotRodClientTestingUtil.killServers;
import static org.infinispan.client.hotrod.test.HotRodClientTestingUtil.registerSCI;
import static org.infinispan.server.hotrod.test.HotRodTestingUtil.hotRodCacheConfiguration;
import static org.testng.AssertJUnit.assertEquals;
import static org.testng.AssertJUnit.assertNotNull;
import static org.testng.AssertJUnit.assertTrue;

import org.infinispan.client.hotrod.RemoteCache;
import org.infinispan.client.hotrod.RemoteCacheManager;
import org.infinispan.client.hotrod.configuration.ConfigurationBuilder;
import org.infinispan.client.hotrod.test.HotRodClientTestingUtil;
import org.infinispan.manager.EmbeddedCacheManager;
import org.infinispan.protostream.GeneratedSchema;
import org.infinispan.protostream.ProtobufUtil;
import org.infinispan.protostream.annotations.ProtoField;
import org.infinispan.protostream.annotations.ProtoName;
import org.infinispan.protostream.annotations.ProtoSchema;
import org.infinispan.server.hotrod.HotRodServer;
import org.infinispan.test.SingleCacheManagerTest;
import org.infinispan.test.fwk.CleanupAfterMethod;
import org.infinispan.test.fwk.TestCacheManagerFactory;
import org.testng.annotations.AfterClass;
import org.testng.annotations.Test;

/**
 * Tests integration between HotRod client and ProtoStream marshalling library using the {@code @ProtoXyz} annotations.
 *
 * @author anistor@redhat.com
 * @since 7.1
 */
@Test(testName = "client.hotrod.marshall.ProtoStreamMarshallerWithAnnotationsTest", groups = "functional")
@CleanupAfterMethod
public class ProtoStreamMarshallerWithAnnotationsTest extends SingleCacheManagerTest {

   private HotRodServer hotRodServer;
   private RemoteCacheManager remoteCacheManager;
   private RemoteCache<Integer, AnnotatedUser> remoteCache;

   @ProtoName("User")
   public static class AnnotatedUser {

      private int id;

      private String name;

      @ProtoField(number = 1, defaultValue = "0")
      public int getId() {
         return id;
      }

      public void setId(int id) {
         this.id = id;
      }

      @ProtoField(2)
      public String getName() {
         return name;
      }

      public void setName(String name) {
         this.name = name;
      }

      @Override
      public String toString() {
         return "AnnotatedUser{id=" + id + ", name='" + name + "'}";
      }
   }

   @Override
   protected EmbeddedCacheManager createCacheManager() throws Exception {
      cacheManager = TestCacheManagerFactory.createCacheManager(hotRodCacheConfiguration());
      cache = cacheManager.getCache().getAdvancedCache().withStorageMediaType();

      hotRodServer = HotRodClientTestingUtil.startHotRodServer(cacheManager);

      ConfigurationBuilder clientBuilder = HotRodClientTestingUtil.newRemoteConfigurationBuilder();
      clientBuilder.addServer().host("127.0.0.1").port(hotRodServer.getPort());
      remoteCacheManager = new RemoteCacheManager(clientBuilder.build());
      remoteCache = remoteCacheManager.getCache();
      registerSCI(remoteCacheManager, ProtoStreamMarshallerWithAnnotationsTestSCI.INSTANCE);
      return cacheManager;
   }

   @AfterClass(alwaysRun = true)
   public void release() {
      killRemoteCacheManager(remoteCacheManager);
      remoteCacheManager = null;
      killServers(hotRodServer);
      hotRodServer = null;
   }

   public void testPutAndGet() throws Exception {
      AnnotatedUser user = createUser();
      remoteCache.put(1, user);

      // try to get the object through the local cache interface and check it's the same object we put
      assertEquals(1, cache.keySet().size());
      byte[] key = (byte[]) cache.keySet().iterator().next();
      Object localObject = cache.get(key);
      assertNotNull(localObject);
      assertTrue(localObject instanceof byte[]);
      Object unmarshalledObject = ProtobufUtil.fromWrappedByteArray(MarshallerUtil.getSerializationContext(remoteCacheManager), (byte[]) localObject);
      assertTrue(unmarshalledObject instanceof AnnotatedUser);
      assertUser((AnnotatedUser) unmarshalledObject);

      // get the object through the remote cache interface and check it's the same object we put
      AnnotatedUser fromRemoteCache = remoteCache.get(1);
      assertUser(fromRemoteCache);
   }

   private AnnotatedUser createUser() {
      AnnotatedUser user = new AnnotatedUser();
      user.setId(33);
      user.setName("Tom");
      return user;
   }

   private void assertUser(AnnotatedUser user) {
      assertNotNull(user);
      assertEquals(33, user.getId());
      assertEquals("Tom", user.getName());
   }

   @ProtoSchema(
         includeClasses = {AnnotatedUser.class},
         schemaFileName = "test.client.ProtoStreamMarshallerWithAnnotationsTest.proto",
         schemaFilePath = "proto/generated",
         service = false
   )
   public interface ProtoStreamMarshallerWithAnnotationsTestSCI extends GeneratedSchema {
      GeneratedSchema INSTANCE = new ProtoStreamMarshallerWithAnnotationsTestSCIImpl();
   }
}
