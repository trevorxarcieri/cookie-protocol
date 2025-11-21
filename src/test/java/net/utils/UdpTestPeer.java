package net.utils;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.regex.Pattern;

import org.junit.jupiter.api.Assertions;

public final class UdpTestPeer implements AutoCloseable {

    /** One scripted operation (receive or send). */
    public interface ScriptStep {
        void perform(DatagramSocket socket) throws IOException;
    }

    /** Receive one packet and assert its text matches a regex. */
    public static final class ReceiveStep implements ScriptStep {
        private final Pattern pattern;

        public ReceiveStep(Pattern pattern) {
            this.pattern = pattern;
        }

        @Override
        public void perform(DatagramSocket socket) throws IOException {
            DatagramPacket packet = new DatagramPacket(new byte[2048], 2048);
            socket.receive(packet);

            String received = new String(packet.getData()).trim();

            if (this.pattern != null) {
                boolean matches = this.pattern.matcher(received).matches();
                if (!matches) {
                    Assertions.fail(
                            "UDP peer expected text matching regex " + this.pattern
                                    + " but received: \"" + received + "\"");
                }
            }
        }
    }

    /** Send one packet to an explicit address/port. */
    public static final class SendStep implements ScriptStep {
        private final String payload;
        private final InetAddress addr;
        private final int port;

        public SendStep(String payload, InetAddress addr, Integer port) {
            this.payload = payload;
            this.addr = addr;
            this.port = port;
        }

        @Override
        public void perform(DatagramSocket socket) throws IOException {
            byte[] bytes = payload.getBytes();
            DatagramPacket packet = new DatagramPacket(bytes, bytes.length, this.addr, this.port);
            socket.send(packet);
        }
    }

    /** Convenience factories */
    public static ReceiveStep recv(String regex) {
        return new ReceiveStep(Pattern.compile(regex));
    }

    public static SendStep send(String payload, InetAddress addr, int port) {
        return new SendStep(payload, addr, port);
    }

    // -------------------------------------------------------------------------
    // Runner
    // -------------------------------------------------------------------------

    private final DatagramSocket socket;
    private final ExecutorService executor;
    private final Future<?> scriptFuture;

    private UdpTestPeer(int listenPort, ScriptStep[] steps) throws IOException {
        this.socket = new DatagramSocket(new InetSocketAddress(InetAddress.getByName("localhost"), listenPort));
        this.executor = Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "UdpTestPeer");
            t.setDaemon(true);
            return t;
        });

        this.scriptFuture = executor.submit(() -> runScript(steps));
    }

    /** Public API */
    public static UdpTestPeer start(int listenPort, ScriptStep... steps) throws IOException {
        return new UdpTestPeer(listenPort, steps);
    }

    private void runScript(ScriptStep[] steps) {
        try {
            for (ScriptStep step : steps) {
                step.perform(this.socket);
            }
        } catch (IOException e) {
            throw new RuntimeException("UdpTestPeer IO error", e);
        } finally {
            close();
        }
    }

    /** Wait for script to finish, propagate assertion failures. */
    public void await(long timeoutMs) {
        try {
            this.scriptFuture.get(timeoutMs, TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt(); // restore interrupt status
            throw new AssertionError("UdpTestPeer was interrupted while waiting", e); // fail the test
        } catch (TimeoutException e) {
            throw new AssertionError("UdpTestPeer timed out after " + timeoutMs + " ms", e);
        } catch (ExecutionException e) {
            if (e.getCause() instanceof AssertionError ae) {
                throw ae; // rethrow assertion failures
            }
            throw new RuntimeException(e.getCause());
        }
    }

    @Override
    public void close() {
        this.socket.close();
        this.executor.shutdownNow();
    }
}
