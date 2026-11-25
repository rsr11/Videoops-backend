
package videoops_api.job;

import jakarta.persistence.LockModeType;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import org.springframework.data.jpa.repository.Modifying;
import java.time.OffsetDateTime;

import java.util.List;

import java.util.Optional;
import java.util.UUID;

public interface VideoJobRepository
        extends JpaRepository<VideoJobEntity, UUID> {

    
@Modifying(clearAutomatically = true)
@Query("""
    UPDATE VideoJobEntity j
    SET j.status = :failedStatus,
        j.error = :reason,
        j.updatedAt = :updatedAt
    WHERE j.status IN :incompleteStatuses
    """)
int failIncompleteJobs(
    @Param("incompleteStatuses")
    List<JobStatus> incompleteStatuses,

    @Param("failedStatus")
    JobStatus failedStatus,

    @Param("reason")
    String reason,

    @Param("updatedAt")
    OffsetDateTime updatedAt
);            

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT j FROM VideoJobEntity j WHERE j.id = :id")
    Optional<VideoJobEntity> findByIdForUpdate(
            @Param("id") UUID id
    );




@Modifying(clearAutomatically = true)
@Query("""
    UPDATE VideoJobEntity j
    SET j.status = :processing,
        j.updatedAt = :now
    WHERE j.id = :jobId
      AND j.status = :pending
    """)
int claimPendingJob(
    @Param("jobId") UUID jobId,
    @Param("pending") JobStatus pending,
    @Param("processing") JobStatus processing,
    @Param("now") OffsetDateTime now
);


Optional<VideoJobEntity> findByIdempotencyKey(
    String idempotencyKey
);

}
