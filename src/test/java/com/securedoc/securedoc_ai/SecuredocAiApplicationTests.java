package com.securedoc.securedoc_ai;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

@SpringBootTest
@TestPropertySource(properties = {
		"spring.datasource.url=jdbc:h2:mem:securedoc-smoke-test;DB_CLOSE_DELAY=-1;MODE=PostgreSQL",
		"spring.datasource.driver-class-name=org.h2.Driver",
		"spring.datasource.username=sa",
		"spring.datasource.password=",
		"spring.jpa.hibernate.ddl-auto=validate",
		"spring.jpa.show-sql=false",
		"spring.flyway.locations=classpath:db/migration/common,classpath:db/migration/h2",
		"securedoc.ai.ollama.semantic-search-enabled=false"
})
class SecuredocAiApplicationTests {

	@Test
	void contextLoads() {
	}

}
