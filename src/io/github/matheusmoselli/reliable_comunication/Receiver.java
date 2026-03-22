package io.github.matheusmoselli.reliable_comunication;
import java.io.*;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.util.*;

public class Receiver {
    private static final Map<Integer, SegmentoConfiavel> BufferOutOfOrder = new HashMap<>();
    private static final Set<Integer> ReceivedIds = new HashSet<>();
    private static int NextSequenceNumber = 0;
    private static final Integer ReceiverPort = 9876;

    public static void main(String[] args) throws Exception {
        DatagramSocket serverSocket = new DatagramSocket(ReceiverPort);

        try {
            while (true) {
                SegmentoConfiavel segReceived = new SegmentoConfiavel(serverSocket);

                ThreadAtendimento thread = new ThreadAtendimento(segReceived, serverSocket);
                thread.start();
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

    private static synchronized void processarSegmento(SegmentoConfiavel seg, DatagramSocket serverSocket) throws IOException {
        int id = seg.getNumeroSequencia();

        // Funcionalidade 4: tratamento de pacotes duplicados
        if (ReceivedIds.contains(id)) {
            System.out.println("Mensagem id " + id + " recebida de forma duplicada");
            // Reenvia ACK mesmo assim — o Sender pode ter não recebido o ACK anterior
            SendAck(seg, serverSocket);
            return;
        }

        // Funcionalidade 3: tratamento de pacotes fora de ordem
        if (id != NextSequenceNumber) {
            // Guarda no buffer para entregar quando os anteriores chegarem
            BufferOutOfOrder.put(id, seg);
            ReceivedIds.add(id);

            // Monta lista dos ids ainda não recebidos até o atual
            List<Integer> naoRecebidos = new ArrayList<>();
            for (int i = NextSequenceNumber; i < id; i++) {
                if (!ReceivedIds.contains(i)) {
                    naoRecebidos.add(i);
                }
            }

            System.out.println(
                    "Mensagem id " + id +
                            " recebida fora de ordem, ainda não recebidos os identificadores " +
                            naoRecebidos
            );

            SendAck(seg, serverSocket);
            return;
        }

        // Chegou na ordem — entrega para a camada de aplicação
        entregarNaOrdem(seg, serverSocket);
    }

    private static synchronized void entregarNaOrdem(SegmentoConfiavel seg, DatagramSocket serverSocket) throws IOException {
        int id = seg.getNumeroSequencia();

        ReceivedIds.add(id);
        NextSequenceNumber++;

        System.out.println(
                "Mensagem id " + id +
                        " recebida na ordem, entregando para a camada de aplicação."
        );

        SendAck(seg, serverSocket);

        // Funcionalidade 6: verifica se há pacotes no buffer que agora podem ser entregues em ordem
        while (BufferOutOfOrder.containsKey(NextSequenceNumber)) {
            SegmentoConfiavel segBuffered = BufferOutOfOrder.remove(NextSequenceNumber);
            int idBuffered = segBuffered.getNumeroSequencia();

            NextSequenceNumber++;

            System.out.println(
                    "Mensagem id " + idBuffered +
                            " recebida na ordem, entregando para a camada de aplicação."
            );

            // Não reenvia ACK aqui pois já foi enviado quando chegou fora de ordem
        }
    }

    public static class ThreadAtendimento extends Thread {
        private final SegmentoConfiavel segReceived;
        private final DatagramSocket socket;

        public ThreadAtendimento(SegmentoConfiavel segReceived, DatagramSocket serverSocket) {
            this.segReceived = segReceived;
            this.socket = serverSocket;
        }

        @Override
        public void run() {
            try {
                processarSegmento(segReceived, socket);
            } catch (Exception e) {
                // encerra silenciosamente quando o socket for fechado
            }
        }
    }
}
