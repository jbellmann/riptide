package org.zalando.riptide;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;
import org.springframework.test.web.client.MockRestServiceServer;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

// this test is just a compile time check for checked exceptions (throws clauses)
@RunWith(MockitoJUnitRunner.class)
public final class ExceptionHandlingTest {

    private final Rest unit;
    private final MockRestServiceServer server;

    @Mock
    private RoutingTree<Void> tree;

    public ExceptionHandlingTest() {
        final MockSetup setup = new MockSetup();
        this.unit = setup.getRest();
        this.server = setup.getServer();
    }

    @Before
    public void setUp() throws Exception {
        server.expect(requestTo("https://api.example.com/"))
                .andRespond(withSuccess());
    }

    @After
    public void tearDown() throws Exception {
        server.verify();
    }

    @Test
    public void shouldNotThrowIOExceptionWhenSettingBody() {
        unit.get("/")
                .body("body");
    }

    @Test
    public void shouldNotThrowIOExceptionWhenDispatchingWithoutBody() {
        unit.get("/")
                .call(tree);
    }

    @Test
    public void shouldNotThrowInterruptedAndExecutionExceptionWhenBlocking() {
        unit.get("/").dispatch(tree).join();
    }

    @Test
    public void shouldThrowInterruptedExecutionAndTimeoutExceptionWhenBlocking() throws InterruptedException,
            ExecutionException, TimeoutException {

        unit.get("/")
                .body("")
                .call(tree).get(10, SECONDS);
    }

}