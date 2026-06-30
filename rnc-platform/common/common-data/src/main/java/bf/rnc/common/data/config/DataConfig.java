package bf.rnc.common.data.config;

import bf.rnc.common.data.auditor.RncAuditorAware;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.domain.AuditorAware;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

@Configuration
@EnableJpaAuditing(auditorAwareRef = "rncAuditorAware")
@EnableJpaRepositories(basePackages = "bf.rnc")
public class DataConfig {

    @Bean
    public AuditorAware<String> rncAuditorAware() {
        return new RncAuditorAware();
    }
}
