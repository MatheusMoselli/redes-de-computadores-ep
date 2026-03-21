package io.github.matheusmoselli.reliable_comunication;
import java.io.*;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.Socket;
import java.util.ArrayList;
import java.util.List;

public class Receiver {
    private static final List<Integer> ReceivedIds = new ArrayList<>();
    private static final Integer ReceiverPort = 9876;

    public static void main(String[] args) throws Exception {
        DatagramSocket serverSocket = new DatagramSocket(ReceiverPort);

        try {
            while (true) {
                SegmentoConfiavel segReceived = new SegmentoConfiavel(serverSocket);
                ReceivedIds.add(segReceived.getNumeroSequencia());

                System.out.println(
                        "Mensagem id " +
                        segReceived.getNumeroSequencia() +
                        " recebida na ordem, entregando para a camada de aplicação."
                );

                SendAck(segReceived, serverSocket);
            }
        }
        finally {
            serverSocket.close();
        }
    }

    private static void SendAck(SegmentoConfiavel segReceived, DatagramSocket serverSocket) throws IOException {
        SegmentoConfiavel ack = new SegmentoConfiavel();
        ack.setIsAck(true);
        ack.setNumeroAck(segReceived.getNumeroSequencia());

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ObjectOutputStream oos = new ObjectOutputStream(baos);
        oos.writeObject(ack);
        byte[] sendData = baos.toByteArray();

        DatagramPacket sendPacket = new DatagramPacket(
                sendData,
                sendData.length,
                segReceived.getEnderecoIP(),
                segReceived.getPorta()
        );

        serverSocket.send(sendPacket);
    }

    public class ThreadAtendimento extends Thread {
        private Socket no;

        public ThreadAtendimento(Socket node) {
            this.no = node;
        }

        public void run() {
            try {
                InputStreamReader is = new InputStreamReader(no.getInputStream());
                BufferedReader reader = new BufferedReader(is);

                OutputStream os = no.getOutputStream();
                DataOutputStream writer = new DataOutputStream(os);

                String texto = reader.readLine();

                writer.writeBytes(texto.toUpperCase() + "\n");
            } catch (Exception e) { }
        }
    }
}
