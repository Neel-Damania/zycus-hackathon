package com.zycus.hackathon.sales;

import java.time.LocalDateTime;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface SaleRepository extends JpaRepository<Sale, Long> {

    @Query("""
            SELECT COALESCE(SUM(s.quantity), 0)
            FROM Sale s
            WHERE s.productId = :productId
              AND s.soldAt >= :start
              AND s.soldAt < :end
              AND s.status = :status
            """)
    long sumUnits(
            @Param("productId") Long productId,
            @Param("start") LocalDateTime start,
            @Param("end") LocalDateTime end,
            @Param("status") SaleStatus status);
}
