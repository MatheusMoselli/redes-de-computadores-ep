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
    private static List<Integer> SentPackages = new ArrayList<>();
    private static List<Integer> AcknowledgedPackages = new ArrayList<>();
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
        UserInputDTO userInput = getUserInput();
        if (userInput == null) return;

        DatagramSocket clientSocket = new DatagramSocket();

        try {
            SegmentPacketDTO segPkt = CreateSegmentPackage(userInput.userMessage);
            clientSocket.send(segPkt.sendPacket);

            System.out.println(
                    "Mensagem " +
                            "\"" +
                            segPkt.originalSegment.getDados() +
                            "\"" +
                            " enviada como " +
                            userInput.transmissionType +
                            " com id " +
                            segPkt.originalSegment.getNumeroSequencia());

            SentPackages.add(segPkt.originalSegment.getNumeroSequencia());

            switch (userInput.transmissionType) {
                case "lenta":
                    break;
                case "perda":
                    break;
                case "fora de ordem":
                    break;
                case "duplicada":
                    break;
                default:
                    break;
            }

            SegmentoConfiavel segReceived = new SegmentoConfiavel(clientSocket);

            if (segReceived.getIsAck()) {
                AcknowledgedPackages.add(segReceived.getNumeroAck());
                System.out.println("Mensagem id " + segReceived.getNumeroAck() + " recebida pelo receiver.");
            }
        } finally {
            clientSocket.close();
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

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ObjectOutputStream oos = new ObjectOutputStream(baos);
        oos.writeObject(originalSegment);
        byte[] sendData = baos.toByteArray();

        DatagramPacket sendPacket = new DatagramPacket(sendData, sendData.length, IPAddress, ReceiverPort);
        return new SegmentPacketDTO(originalSegment, sendPacket);
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
