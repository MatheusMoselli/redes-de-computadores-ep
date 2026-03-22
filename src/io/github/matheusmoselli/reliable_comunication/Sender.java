package io.github.matheusmoselli.reliable_comunication;

import java.io.*;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.*;

public class Sender {
    private static Integer CurrentSequenceNumber = 0;
    private static final List<Integer> PendingAcknowledgements = new ArrayList<>();
    private static final Set<Integer> AcknowledgedPackages = new HashSet<>();
    private static final int TIMEOUT_MS = 3000;
    private static final int SLOW_DELAY_MS = TIMEOUT_MS + 2000;
    private static final Integer ReceiverPort = 9876;
    private static final InetAddress IPAddress;

    static {
        try {
            IPAddress = InetAddress.getByName("127.0.0.1");
        } catch (UnknownHostException e) {
            throw new RuntimeException(e);
        }
    }

    public static void main(String[] args) throws Exception {
        DatagramSocket clientSocket = new DatagramSocket();

        AckListenerThread ackListener = new AckListenerThread(clientSocket);
        ackListener.start();

        try {
            while (true) {
                UserInputDTO userInput = getUserInput();
                if (userInput == null) continue;

                enviar(clientSocket, userInput.getUserMessage(), userInput.getTransmissionType());
            }
        } finally {
            ackListener.interrupt();
            clientSocket.close();
        }
    }

    private static void enviar(DatagramSocket socket, String mensagem, String transmissionType) throws Exception {
        if (transmissionType.equals("fora de ordem")) {
            enviarForaDeOrdem(socket, mensagem);
            return;
        }

        SegmentPacketDTO segPkt = criarSegmento(mensagem);
        SegmentoConfiavel seg = segPkt.getOriginalSegment();
        int id = seg.getNumeroSequencia();

        synchronized (PendingAcknowledgements) {
            PendingAcknowledgements.add(id);
        }

        System.out.println(
                "Mensagem \"" + mensagem + "\" enviada como " + transmissionType + " com id " + id
        );

        switch (transmissionType) {
            case "lenta":
                Thread.sleep(SLOW_DELAY_MS);
                socket.send(segPkt.getSendPacket());
                break;

            case "perda":
                break;

            case "duplicada":
                socket.send(segPkt.getSendPacket());
                socket.send(segPkt.getSendPacket());
                break;

            default:
                socket.send(segPkt.getSendPacket());
                break;
        }

        aguardarAckComReenvio(socket, seg, id);
    }

    private static void enviarForaDeOrdem(DatagramSocket socket, String mensagem) throws Exception {
        SegmentPacketDTO segPkt1 = criarSegmento(mensagem);
        SegmentPacketDTO segPkt2 = criarSegmento(mensagem);

        SegmentoConfiavel seg1 = segPkt1.getOriginalSegment();
        SegmentoConfiavel seg2 = segPkt2.getOriginalSegment();

        int id1 = seg1.getNumeroSequencia();
        int id2 = seg2.getNumeroSequencia();

        synchronized (PendingAcknowledgements) {
            PendingAcknowledgements.add(id1);
            PendingAcknowledgements.add(id2);
        }

        System.out.println("Mensagem \"" + mensagem + "\" enviada como fora de ordem com id " + id1);
        System.out.println("Mensagem \"" + mensagem + "\" enviada como fora de ordem com id " + id2);

        socket.send(segPkt2.getSendPacket());
        socket.send(segPkt1.getSendPacket());

        aguardarAckComReenvio(socket, seg1, id1);
        aguardarAckComReenvio(socket, seg2, id2);
    }

    private static void aguardarAckComReenvio(DatagramSocket socket, SegmentoConfiavel seg, int id) throws Exception {
        while (true) {
            synchronized (AcknowledgedPackages) {
                if (AcknowledgedPackages.contains(id)) return;
            }

            Thread.sleep(TIMEOUT_MS);

            synchronized (AcknowledgedPackages) {
                if (AcknowledgedPackages.contains(id)) return;
            }

            System.out.println("Mensagem id " + id + " deu timeout, reenviando.");

            SegmentPacketDTO pkt = prepararPacote(seg);
            socket.send(pkt.getSendPacket());
        }
    }

    private static UserInputDTO getUserInput() throws IOException {
        BufferedReader inFromUser = new BufferedReader(new InputStreamReader(System.in));
        String userMessage = inFromUser.readLine();

        System.out.println("Digite uma opção de envio entre: lenta, perda, fora de ordem, duplicada e normal");
        String transmissionType = inFromUser.readLine();

        List<String> validTypes = Collections.unmodifiableList(
                Arrays.asList("lenta", "perda", "fora de ordem", "duplicada", "normal")
        );

        if (!validTypes.contains(transmissionType)) {
            System.out.println("Tipo de envio inválido, tente novamente.");
            return null;
        }

        return new UserInputDTO(userMessage, transmissionType);
    }

    private static SegmentPacketDTO criarSegmento(String mensagem) throws IOException {
        SegmentoConfiavel seg = new SegmentoConfiavel();
        seg.setNumeroSequencia(CurrentSequenceNumber++);
        seg.setIsAck(false);
        seg.setDados(mensagem);
        seg.setTimestampEnvio(System.currentTimeMillis());
        seg.setPorta(ReceiverPort);
        seg.setEnderecoIP(IPAddress);

        return prepararPacote(seg);
    }

    private static SegmentPacketDTO prepararPacote(SegmentoConfiavel seg) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ObjectOutputStream oos = new ObjectOutputStream(baos);
        oos.writeObject(seg);
        byte[] sendData = baos.toByteArray();

        DatagramPacket pkt = new DatagramPacket(sendData, sendData.length, IPAddress, ReceiverPort);
        return new SegmentPacketDTO(seg, pkt);
    }

    private static class AckListenerThread extends Thread {
        private final DatagramSocket socket;

        public AckListenerThread(DatagramSocket socket) {
            this.socket = socket;
            setDaemon(true);
        }

        @Override
        public void run() {
            while (!isInterrupted()) {
                try {
                    SegmentoConfiavel ack = new SegmentoConfiavel(socket);

                    if (ack.getIsAck()) {
                        int idConfirmado = ack.getNumeroAck();

                        synchronized (PendingAcknowledgements) {
                            PendingAcknowledgements.remove(idConfirmado);
                        }
                        synchronized (AcknowledgedPackages) {
                            AcknowledgedPackages.add(idConfirmado);
                        }

                        System.out.println("Mensagem id " + idConfirmado + " recebida pelo receiver.");
                    }
                } catch (Exception e) { }
            }
        }
    }

    private static class UserInputDTO {
        private final String userMessage;
        private final String transmissionType;

        public UserInputDTO(String userMessage, String transmissionType) {
            this.userMessage = userMessage;
            this.transmissionType = transmissionType;
        }

        public String getUserMessage() { return userMessage; }
        public String getTransmissionType() { return transmissionType; }
    }

    private static class SegmentPacketDTO {
        private final SegmentoConfiavel originalSegment;
        private final DatagramPacket sendPacket;

        public SegmentPacketDTO(SegmentoConfiavel originalSegment, DatagramPacket sendPacket) {
            this.originalSegment = originalSegment;
            this.sendPacket = sendPacket;
        }

        public SegmentoConfiavel getOriginalSegment() { return originalSegment; }
        public DatagramPacket getSendPacket() { return sendPacket; }
    }
}