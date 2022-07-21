package org.infinispan.client.hotrod.impl.operations;

import java.util.concurrent.atomic.AtomicInteger;

import net.jcip.annotations.Immutable;
import org.infinispan.client.hotrod.ProtocolVersion;
import org.infinispan.client.hotrod.configuration.Configuration;
import org.infinispan.client.hotrod.impl.protocol.Codec;
import org.infinispan.client.hotrod.impl.transport.netty.ChannelFactory;

/**
 * An extension of {@link HotRodOperation} for backwards compatibility after introducing HR 4.0. We override the
 * <code>codec</code> with a pinned version, all operations are executed in the same way.
 *
 * @param <T>
 */
@Immutable
public abstract class NeutralVersionHotRodOperation<T> extends HotRodOperation<T> {

   protected NeutralVersionHotRodOperation(short requestCode, short responseCode, Codec codec, int flags, Configuration cfg,
                                           byte[] cacheName, AtomicInteger topologyId, ChannelFactory channelFactory) {
      super(requestCode, responseCode, chooseCodec(codec), flags, cfg, cacheName, topologyId, channelFactory);
   }

   private static Codec chooseCodec(Codec codec) {
      return codec.isUnsafeForTheHandshake() ? ProtocolVersion.SAFE_HANDSHAKE_PROTOCOL_VERSION.getCodec() : codec;
   }
}
