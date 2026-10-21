package com.zycus.hackathon.product;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface InventoryRepository extends JpaRepository<Inventory, Long> {

    // Product is lazy; callers map to a snapshot outside the persistence context.
    @EntityGraph(attributePaths = "product")
    Optional<Inventory> findByProductId(Long productId);

    @EntityGraph(attributePaths = "product")
    List<Inventory> findAllByOrderByProductIdAsc();

    @Query("SELECT i.product.id FROM Inventory i ORDER BY i.product.id")
    List<Long> findAllProductIds();

    /** Removes sold units only if enough are on hand; returns 0 otherwise. */
    @Modifying(clearAutomatically = true)
    @Query("""
            UPDATE Inventory i
            SET i.currentStock = i.currentStock - :quantity,
                i.lastUpdated = CURRENT_TIMESTAMP
            WHERE i.product.id = :productId
              AND i.currentStock >= :quantity
            """)
    int decreaseStock(@Param("productId") Long productId, @Param("quantity") long quantity);

    @Modifying(clearAutomatically = true)
    @Query("""
            UPDATE Inventory i
            SET i.onOrderStock = i.onOrderStock + :quantity,
                i.lastUpdated = CURRENT_TIMESTAMP
            WHERE i.product.id = :productId
            """)
    int addOnOrder(@Param("productId") Long productId, @Param("quantity") long quantity);

    /** Received units leave the on-order pool; anything beyond it was an unplanned delivery. */
    @Modifying(clearAutomatically = true)
    @Query("""
            UPDATE Inventory i
            SET i.currentStock = i.currentStock + :quantity,
                i.onOrderStock = CASE WHEN i.onOrderStock > :quantity
                                      THEN i.onOrderStock - :quantity ELSE 0 END,
                i.lastUpdated = CURRENT_TIMESTAMP
            WHERE i.product.id = :productId
            """)
    int receiveStock(@Param("productId") Long productId, @Param("quantity") long quantity);
}
