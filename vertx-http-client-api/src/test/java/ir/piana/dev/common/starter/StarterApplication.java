package ir.piana.dev.common.starter;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.scheduling.annotation.EnableScheduling;

import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

@SpringBootApplication(scanBasePackages = {"ir.piana.dev"})
@EnableScheduling
public class StarterApplication {

	public static void main(String[] args) {
		new SpringApplication(StarterApplication.class).run(args);
	}

	@Bean
	public Executor taskExecutor() {
		return Executors.newFixedThreadPool(10);
	}

}
