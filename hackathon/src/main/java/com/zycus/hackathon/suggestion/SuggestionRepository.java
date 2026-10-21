package com.zycus.hackathon.suggestion;

import java.time.Instant;
import java.util.List;

import org.springframework.data.domain.Limit;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface SuggestionRepository extends JpaRepository<Suggestion, Long> {

    boolean existsByProductIdAndTypeAndStatus(Long productId, SuggestionType type, SuggestionStatus status);

    boolean existsByProductIdAndTypeAndStatusAndReviewedAtAfter(
            Long productId, SuggestionType type, SuggestionStatus status, Instant reviewedAfter);

    List<Suggestion> findByProductIdAndStatusOrderByReviewedAtDesc(
            Long productId, SuggestionStatus status, Limit limit);

    /** A null filter matches everything. */
    @Query("""
            SELECT s FROM Suggestion s
            WHERE (:productId IS NULL OR s.productId = :productId)
              AND (:type IS NULL OR s.type = :type)
              AND (:status IS NULL OR s.status = :status)
            """)
    List<Suggestion> search(
            @Param("productId") Long productId,
            @Param("type") SuggestionType type,
            @Param("status") SuggestionStatus status,
            Sort sort);

    /** Moves a PENDING suggestion to a final status; returns 0 if it is missing or already reviewed. */
    @Modifying(clearAutomatically = true)
    @Query("""
            UPDATE Suggestion s
            SET s.status = :status, s.reviewedAt = :reviewedAt, s.reviewNote = :note
            WHERE s.id = :id
              AND s.status = com.zycus.hackathon.suggestion.SuggestionStatus.PENDING
            """)
    int review(
            @Param("id") Long id,
            @Param("status") SuggestionStatus status,
            @Param("reviewedAt") Instant reviewedAt,
            @Param("note") String note);
}
