package org.infinispan.objectfilter.impl.syntax;

/**
 * Checks if there are any full-text or spatial predicates in a query. These search predicates require indexing.
 *
 * @author anistor@redhat.com
 * @since 9.0
 */
public final class IndexedSearchPredicateDetector implements Visitor<Boolean, Boolean> {

   private static final IndexedSearchPredicateDetector INSTANCE = new IndexedSearchPredicateDetector();

   private IndexedSearchPredicateDetector() {
   }

   public static boolean checkIndexingRequired(BooleanExpr expr) {
      return expr.acceptVisitor(INSTANCE);
   }

   @Override
   public Boolean visit(FullTextOccurExpr fullTextOccurExpr) {
      return Boolean.TRUE;
   }

   @Override
   public Boolean visit(FullTextBoostExpr fullTextBoostExpr) {
      return Boolean.TRUE;
   }

   @Override
   public Boolean visit(FullTextTermExpr fullTextTermExpr) {
      return Boolean.TRUE;
   }

   @Override
   public Boolean visit(FullTextRegexpExpr fullTextRegexpExpr) {
      return Boolean.TRUE;
   }

   @Override
   public Boolean visit(FullTextRangeExpr fullTextRangeExpr) {
      return Boolean.TRUE;
   }

   @Override
   public Boolean visit(SpatialWithinCircleExpr spatialWithinCircleExpr) {
      return Boolean.TRUE;
   }

   @Override
   public Boolean visit(SpatialWithinBoxExpr spatialWithinBoxExpr) {
      return Boolean.TRUE;
   }

   @Override
   public Boolean visit(SpatialWithinPolygonExpr spatialWithinPolygonExpr) {
      return Boolean.TRUE;
   }

   @Override
   public Boolean visit(KnnPredicate knnPredicate) {
      return Boolean.TRUE;
   }

   @Override
   public Boolean visit(NotExpr notExpr) {
      return notExpr.getChild().acceptVisitor(this);
   }

   @Override
   public Boolean visit(OrExpr orExpr) {
      for (BooleanExpr c : orExpr.getChildren()) {
         if (c.acceptVisitor(this)) {
            return Boolean.TRUE;
         }
      }
      return Boolean.FALSE;
   }

   @Override
   public Boolean visit(AndExpr andExpr) {
      for (BooleanExpr c : andExpr.getChildren()) {
         if (c.acceptVisitor(this)) {
            return Boolean.TRUE;
         }
      }
      return Boolean.FALSE;
   }

   @Override
   public Boolean visit(ConstantBooleanExpr constantBooleanExpr) {
      return Boolean.FALSE;
   }

   @Override
   public Boolean visit(IsNullExpr isNullExpr) {
      return isNullExpr.getChild().acceptVisitor(this);
   }

   @Override
   public Boolean visit(ComparisonExpr comparisonExpr) {
      return Boolean.FALSE;
   }

   @Override
   public Boolean visit(BetweenExpr betweenExpr) {
      return Boolean.FALSE;
   }

   @Override
   public Boolean visit(LikeExpr likeExpr) {
      return Boolean.FALSE;
   }

   @Override
   public Boolean visit(ConstantValueExpr constantValueExpr) {
      return Boolean.FALSE;
   }

   @Override
   public Boolean visit(PropertyValueExpr propertyValueExpr) {
      return Boolean.FALSE;
   }

   @Override
   public Boolean visit(AggregationExpr aggregationExpr) {
      return Boolean.FALSE;
   }

   @Override
   public Boolean visit(NestedExpr nestedExpr) {
      for (BooleanExpr c : nestedExpr.getNestedChildren()) {
         if (c.acceptVisitor(this)) {
            return Boolean.TRUE;
         }
      }
      return Boolean.FALSE;
   }
}
