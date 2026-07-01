package bf.rnc.services.trust.score.repository;

import bf.rnc.services.trust.score.entity.ScoreFactorConfig;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ScoreFactorConfigRepository extends JpaRepository<ScoreFactorConfig, UUID> {

    Optional<ScoreFactorConfig> findByFactorKey(String factorKey);

    List<ScoreFactorConfig> findByEnabledTrue();
}
