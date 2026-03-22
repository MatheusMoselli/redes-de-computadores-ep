package io.github.matheusmoselli.reliable_comunication;
import java.io.*;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class Sender {
    private static Integer CurrentSequenceNumber = 0;
    private static final List<Integer> PendingAcknowledgements = new ArrayList<>();
    private static final List<Integer> AcknowledgedPackages = new ArrayList<>();
    private static final Integer ReceiverPort = 9876;
    private static final InetAddress IPAddress;
    private static final int TIMEOUT_MS = 3000;

    static {
        try {
            IPAddress = InetAddress.getByName("127.0.0.1");
        } catch (UnknownHostException e) {
            throw new RuntimeException(e);
        }
    }

    public static void main(String[] args) throws Exception {
        DatagramSocket clientSocket = new DatagramSocket();
        clientSocket.setSoTimeout(TIMEOUT_MS);

        AckListenerThread ackListener = new AckListenerThread(clientSocket);
        ackListener.start();

        try {
            while (true) {
                UserInputDTO userInput = getUserInput();
                if (userInput == null) continue;

                switch (userInput.getTransmissionType()) {
                    case "lenta":
                        enviarLento(clientSocket, userInput.userMessage);
                        break;
                    case "perda":
                        enviarComPerda(clientSocket, userInput.userMessage);
                        break;
                    case "fora de ordem":
                        enviarForaDeOrdem(clientSocket, userInput.userMessage);
                        break;
                    case "duplicada":
                        enviarDuplicado(clientSocket, userInput.userMessage);
                        break;
                    default:
                        enviarNormal(clientSocket, userInput.userMessage);
                        break;
                }
            }
        } finally {
            ackListener.interrupt();
            clientSocket.close();
        }
    }

    private static void enviarComPerda(DatagramSocket socket, String mensagem) throws Exception {
        SegmentPacketDTO segPkt = CreateSegmentPackage(mensagem);
        int id = segPkt.originalSegment.getNumeroSequencia();

        // Registra no buffer mas NÃO envia — simula perda
        PendingAcknowledgements.add(id);
        System.out.println("Mensagem \"" + mensagem + "\" enviada como perda com id " + id);

        // Aguarda o timeout e reenvia
        aguardarEReenviar(socket, segPkt.originalSegment, id);
    }

    private static void enviarLento(DatagramSocket socket, String mensagem) throws Exception {
        SegmentPacketDTO segPkt = CreateSegmentPackage(mensagem);
        int id = segPkt.originalSegment.getNumeroSequencia();

        PendingAcknowledgements.add(id);
        System.out.println("Mensagem \"" + mensagem + "\" enviada como lenta com id " + id);

        // Atraso artificial de 5 segundos (maior que o TIMEOUT_MS de 3s)
        // O Sender vai dar timeout e reenviar antes do pacote chegar
        Thread.sleep(TIMEOUT_MS + 1000);
        socket.send(segPkt.sendPacket);

        aguardarEReenviar(socket, segPkt.originalSegment, id);
    }

    private static void enviarForaDeOrdem(DatagramSocket socket, String mensagem) throws Exception {
        // Cria dois pacotes e inverte a ordem de envio
        SegmentPacketDTO segPkt1 = CreateSegmentPackage(mensagem);
        SegmentPacketDTO segPkt2 = CreateSegmentPackage(mensagem);

        int id1 = segPkt1.originalSegment.getNumeroSequencia();
        int id2 = segPkt2.originalSegment.getNumeroSequencia();

        PendingAcknowledgements.add(id1);
        PendingAcknowledgements.add(id2);

        System.out.println("Mensagem \"" + mensagem + "\" enviada como fora de ordem com id " + id1);
        System.out.println("Mensagem \"" + mensagem + "\" enviada como fora de ordem com id " + id2);

        // Envia o segundo antes do primeiro (fora de ordem)
        socket.send(segPkt2.sendPacket);
        socket.send(segPkt1.sendPacket);
    }

    private static void enviarDuplicado(DatagramSocket socket, String mensagem) throws Exception {
        SegmentPacketDTO segPkt = CreateSegmentPackage(mensagem);
        int id = segPkt.originalSegment.getNumeroSequencia();

        PendingAcknowledgements.add(id);
        System.out.println("Mensagem \"" + mensagem + "\" enviada como duplicada com id " + id);

        // Envia duas vezes o mesmo pacote (mesmo id e numSeq)
        socket.send(segPkt.sendPacket);
        socket.send(segPkt.sendPacket);
    }

    private static void enviarNormal(DatagramSocket socket, String mensagem) throws Exception {
        SegmentPacketDTO segPkt = CreateSegmentPackage(mensagem);
        int id = segPkt.originalSegment.getNumeroSequencia();

        PendingAcknowledgements.add(id);
        socket.send(segPkt.sendPacket);
        System.out.println("Mensagem \"" + mensagem + "\" enviada como normal com id " + id);
    }

    private static void aguardarEReenviar(DatagramSocket socket, SegmentoConfiavel seg, int id) throws Exception {
        while (!AcknowledgedPackages.contains(id)) {
            try {
                Thread.sleep(TIMEOUT_MS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }

            if (!AcknowledgedPackages.contains(id)) {
                // Timeout: reenvia o pacote
                System.out.println("Mensagem id " + id + " deu timeout, reenviando.");

                SegmentPacketDTO pkt = preparePackage(seg);
                socket.send(pkt.getSendPacket());
            }
        }
    }

    private static UserInputDTO getUserInput() throws IOException {
        BufferedReader inFromUser = new BufferedReader(new InputStreamReader(System.in));
        String userMessage = inFromUser.readLine();

        System.out.println("Digite uma opção de envio entre: lenta, perda, fora de ordem, duplicada e normal");
        String transmissionType = inFromUser.readLine();

        List<String> validTransmissionTypes = Collections.unmodifiableList(
                Arrays.asList("lenta", "perda", "fora de ordem", "duplicada", "normal")
        );

        if (!validTransmissionTypes.contains(transmissionType)) {
            System.out.println("Tipo de envio inválido, encerrando programa.");
            return null;
        }
        return new UserInputDTO(userMessage, transmissionType);
    }

    private static SegmentPacketDTO CreateSegmentPackage(String userMessage) throws IOException {
        SegmentoConfiavel originalSegment = new SegmentoConfiavel();
        originalSegment.setNumeroSequencia(CurrentSequenceNumber++);
        originalSegment.setIsAck(false);
        originalSegment.setDados(userMessage);
        originalSegment.setTimestampEnvio(System.currentTimeMillis());
        originalSegment.setPorta(ReceiverPort);
        originalSegment.setEnderecoIP(IPAddress);

        return preparePackage(originalSegment);
    }

    private static SegmentPacketDTO preparePackage(SegmentoConfiavel originalSegment) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ObjectOutputStream oos = new ObjectOutputStream(baos);
        oos.writeObject(originalSegment);
        byte[] sendData = baos.toByteArray();

        DatagramPacket sendPacket = new DatagramPacket(sendData, sendData.length, IPAddress, ReceiverPort);
        return new SegmentPacketDTO(originalSegment, sendPacket);
    }

    private static class AckListenerThread extends Thread {
        private final DatagramSocket socket;

        public AckListenerThread(DatagramSocket socket) {
            this.socket = socket;
            setDaemon(true); // encerra junto com o programa principal
        }

        @Override
        public void run() {
            while (!isInterrupted()) {
                try {
                    SegmentoConfiavel ack = new SegmentoConfiavel(socket);

                    if (ack.getIsAck()) {
                        int idConfirmado = ack.getNumeroAck();

                        PendingAcknowledgements.remove(idConfirmado);
                        AcknowledgedPackages.add(idConfirmado);

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

        public String getUserMessage() {
            return userMessage;
        }

        public String getTransmissionType() {
            return transmissionType;
        }
    }

    private static class SegmentPacketDTO {
        private final SegmentoConfiavel originalSegment;
        private final DatagramPacket sendPacket;

        public SegmentPacketDTO(SegmentoConfiavel originalSegment, DatagramPacket sendPacket) {
            this.originalSegment = originalSegment;
            this.sendPacket = sendPacket;
        }

        public SegmentoConfiavel getOriginalSegment() {
            return originalSegment;
        }

        public DatagramPacket getSendPacket() {
            return sendPacket;
        }
    }
}
