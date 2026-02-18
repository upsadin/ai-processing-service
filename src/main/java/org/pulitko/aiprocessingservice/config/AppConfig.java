package org.pulitko.aiprocessingservice.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.support.JdbcTransactionManager;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.transaction.PlatformTransactionManager;

import javax.sql.DataSource;
import java.util.Map;

@EnableAsync
@Configuration
public class AppConfig {
    @Bean
    public ObjectMapper objectMapper() {
        return JsonMapper.builder()
                .findAndAddModules()
                .build();
    }
    @Bean
    public CommandLineRunner printTransactionManagers(ApplicationContext ctx) {
        return args -> {
            System.out.println("---- ПРОВЕРКА TRANSACTION MANAGERS ----");
            Map<String, PlatformTransactionManager> managers = ctx.getBeansOfType(PlatformTransactionManager.class);
            managers.forEach((name, bean) -> {
                System.out.println("Бин: " + name + " -> Тип: " + bean.getClass().getName());
            });
            System.out.println("---------------------------------------");
        };
    }

    @Bean(name = {"transactionManager", "jdbcTransactionManager"})
    @Primary
    public PlatformTransactionManager jdbcTransactionManager(DataSource dataSource) {
        return new JdbcTransactionManager(dataSource);
    }
}
