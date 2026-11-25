
package videoops_api.job;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

final class TestVideoJobRepository {

    private TestVideoJobRepository() {}

    static VideoJobRepository create( ) {

        VideoJobRepository repository =
                mock(VideoJobRepository.class);

        Map<UUID, VideoJobEntity> data =
                new ConcurrentHashMap<>();

        when(repository.save(any(VideoJobEntity.class)))
            .thenAnswer(invocation -> {
                VideoJobEntity entity =
                    invocation.getArgument(0);

                data.put(entity.getId(), entity);
                return entity;
            });

        when(repository.findById(any(UUID.class)))
            .thenAnswer(invocation -> {
                UUID id = invocation.getArgument(0);
                return Optional.ofNullable(data.get(id));
            });

        when(repository.findByIdForUpdate(any(UUID.class)))
            .thenAnswer(invocation -> {
                UUID id = invocation.getArgument(0);
                return Optional.ofNullable(data.get(id));
            });


            when(repository.claimPendingJob(
        any(UUID.class),
        any(JobStatus.class),
        any(JobStatus.class),
        any(java.time.OffsetDateTime.class)
)).thenAnswer(invocation -> {

    UUID id = invocation.getArgument(0);

    java.util.concurrent.atomic.AtomicBoolean claimed =
        new java.util.concurrent.atomic.AtomicBoolean(false);

    data.computeIfPresent(id, (key, entity) -> {

        if (entity.getStatus() == JobStatus.PENDING) {
            entity.updateStatus(JobStatus.PROCESSING, null);
            claimed.set(true);
        }

        return entity;
    });

    return claimed.get() ? 1 : 0;
});

        return repository;
    }
}
