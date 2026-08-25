package ru.voropaev.event_driven_marketplace;

import org.springframework.boot.SpringApplication;

public class TestEventDrivenMarketplaceApplication {

	public static void main(String[] args) {
		SpringApplication.from(EventDrivenMarketplaceApplication::main).with(TestcontainersConfiguration.class).run(args);
	}

}
