package br.jus.stj.importacaotabelas.config;

import java.util.concurrent.ThreadPoolExecutor;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.TaskExecutor;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

@Configuration
@EnableAsync
public class AsyncConfiguration {


	@Bean("threadPoolTaskExecutor")
	public TaskExecutor getAsyncExecutor() {
		ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
		executor.setCorePoolSize(4);
		executor.setMaxPoolSize(10);
		executor.setQueueCapacity(20);
		//executor.setWaitForTasksToCompleteOnShutdown(waitForTasksToCompleteOnShutdown);
		executor.setThreadNamePrefix("IntegraExecutor-");
		executor.setRejectedExecutionHandler(new ThreadPoolExecutor.AbortPolicy());  // Abort if full
		return executor;
	}
}
