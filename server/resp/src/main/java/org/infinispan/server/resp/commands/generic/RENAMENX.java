package org.infinispan.server.resp.commands.generic;

import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

import org.infinispan.commons.util.concurrent.CompletableFutures;
import org.infinispan.server.resp.Consumers;
import org.infinispan.server.resp.Resp3Handler;
import org.infinispan.server.resp.RespCommand;
import org.infinispan.server.resp.RespRequestHandler;
import org.infinispan.server.resp.commands.Resp3Command;
import org.infinispan.commons.util.concurrent.CompletionStages;

import io.netty.channel.ChannelHandlerContext;

/**
 * RENAMENX Resp Command
 *
 * @link <a href="https://redis.io/commands/renamenx/">RENAMENX</a>
 *       Operation is performed in two step: old key removed, new key set.
 *       data could get lost in case of failure of the latter.
 * @since 15.0
 */
public class RENAMENX extends RespCommand implements Resp3Command {

   public RENAMENX() {
      super(3, 1, 2, 1);
   }

   @Override
   public CompletionStage<RespRequestHandler> perform(Resp3Handler handler,
         ChannelHandlerContext ctx,
         List<byte[]> arguments) {
      byte[] srcKey = arguments.get(0);
      byte[] dstKey = arguments.get(1);
      return rename(handler, srcKey, dstKey, ctx);
   }

   private static CompletionStage<RespRequestHandler> rename(Resp3Handler handler, byte[] srcKey, byte[] dstKey,
         ChannelHandlerContext ctx) {
      if (Arrays.equals(srcKey, dstKey)) {
         // If src = dest return 0
         return handler.stageToReturn(CompletableFuture.completedFuture(0L), ctx, Consumers.LONG_BICONSUMER);
      }

      return CompletionStages.handleAndComposeAsync(handler.cache().containsKeyAsync(dstKey), (contains, t) -> {
         if (t != null) throw CompletableFutures.asCompletionException(t);

         return contains
               ? handler.stageToReturn(CompletableFuture.completedFuture(0L), ctx, Consumers.LONG_BICONSUMER)
               : RENAME.rename(handler, srcKey, dstKey, ctx, Consumers.LONG_BICONSUMER);
      }, ctx.executor());
   }
}
