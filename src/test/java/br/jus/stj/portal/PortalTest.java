package br.jus.stj.portal;

import static org.junit.Assert.assertTrue;

import org.junit.Test;
import org.springframework.test.context.ActiveProfiles;

@ActiveProfiles("test")
public class PortalTest {
    @Test
	public void contextLoads() {
		assertTrue("This will succeed. Dummy Tests", true);
	}

}
