package io.github.matheusmoselli.reliable_comunication;
import java.io.*;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.util.*;

public class Receiver {
    /**
     * Variável criada para facilitar leitura dos ‘inputs’ do usuário
     */
    private static final BufferedReader inFromUser = new BufferedReader(new InputStreamReader(System.in));

    /**
     * Próximo número de sequência esperado
     */
    private static int nextSequenceNumber = 0;

    /**
     * Lista de ids já recebidos
     * Funcionalidade 6: Buffer de ids ja recebidos, usado para detectar duplicatas e controlar a ordem
     */
    private static final Set<Integer> receivedIds = new HashSet<>();

    /**
     * Lista de pacotes recebidos fora de ordem { numSeq: segmentoConfiavel }
     * Funcionalidade 6: Buffer de pacotes recebidos fora de ordem, aguardando entrega sequencial
     */
    private static final Map<Integer, SegmentoConfiavel> bufferOutOfOrder
            = Collections.synchronizedMap(new HashMap<>());

    public static void main(String[] args) throws Exception {
        // Funcionalidade 8: Porta do receiver
        System.out.print("Digite a porta do receiver [default: 9876]: ");

        // Se o valor inserido for vazio (ou seja, apenas o Enter foi pressionado), utilizar 9876
        String portInput = inFromUser.readLine();
        int receiverPort = portInput.isEmpty() ? 9876 : Integer.parseInt(portInput);

        // Cria o 'socket' que irá receber as mensagens e enviar os ACKs
        DatagramSocket serverSocket = new DatagramSocket(receiverPort);

        try {
            // 'Loop' infinito, ou seja, fica sempre ouvindo mensagens
            while (true) {
                // Converte os bytes recebidos em SegmentoConfiavel
                SegmentoConfiavel segReceived = Sender.receber(serverSocket);

                // Processa o SegmentoConfiavel
                processSegment(segReceived, serverSocket);
            }
        }
        finally {
            // Encerra o socket e para de receber mensagens
            serverSocket.close();
        }
    }

    /**
     * Envia o ACK do pacote recebido
     * Funcionalidade 1: Utiliza a classe SegmentoConfiavel como formato do cabeçalho da resposta (ACK)
     * Funcionalidade 7: Envia o ACK de volta ao sender confirmando o recebimento do pacote
     * @param segReceived segmento recebido
     * @param serverSocket 'socket' que recebeu o pacote e irá enviar o ACK
     * @throws IOException Exceção em caso de erro
     */
    private static void sendAck(SegmentoConfiavel segReceived, DatagramSocket serverSocket) throws IOException {
        // Cria o ACK apenas com o 'numeroAck' e 'isAck' true
        SegmentoConfiavel ack = new SegmentoConfiavel();
        ack.setIsAck(true);
        ack.setNumeroAck(segReceived.getNumeroSequencia());

        // Serializa o ACK
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ObjectOutputStream oos = new ObjectOutputStream(baos);
        oos.writeObject(ack);
        byte[] sendData = baos.toByteArray();

        // Envia o ACK para o IP e a porta que enviaram o pacote
        // (Utilizando a porta e endereço de IP do SegmentoConfiavel apenas por comodidade, uma vez que
        // é possível obter esses valores através do DatagramPacket utilizado na obtenção do SegmentoConfiavel)
        DatagramPacket sendPacket = new DatagramPacket(
                sendData,
                sendData.length,
                segReceived.getEnderecoIP(),
                segReceived.getPorta()
        );

        serverSocket.send(sendPacket);
    }

    /**
     * Processa o segmento (valida se está fora de ordem, duplicado, ou normal)
     * @param seg SegmentoConfiavel recebido
     * @param serverSocket 'socket' que recebeu o segmento e irá enviar o ACK
     * @throws IOException Exceção em caso de erro
     */
    private static void processSegment(SegmentoConfiavel seg, DatagramSocket serverSocket) throws IOException {
        int id = seg.getNumeroSequencia();

        // Funcionalidades 2 e 5: O 'receiver' não controla o temporizador diretamente (repetição seletiva)
        // O 'sender' controla o temporizador através do recebimento do ACK de cada pacote

        // Funcionalidade 4: tratamento de pacotes duplicados
        if (receivedIds.contains(id)) {
            System.out.println("Mensagem id " + id + " recebida de forma duplicada");

            // Reenvia ACK para caso o ACK tenha se perdido
            sendAck(seg, serverSocket);
            return;
        }

        // Funcionalidade 3: tratamento de pacotes fora de ordem
        // Pacote recebido não era o esperado
        if (id != nextSequenceNumber) {
            // Guarda no buffer para entregar quando os anteriores chegarem
            bufferOutOfOrder.put(id, seg);
            receivedIds.add(id);

            // Monta lista dos ids ainda não recebidos até o atual para exibir no console
            List<Integer> notReceived = new ArrayList<>();
            for (int i = nextSequenceNumber; i < id; i++) {
                if (!receivedIds.contains(i)) {
                    notReceived.add(i);
                }
            }

            // Funcionalidade 7: Exibe na console do receiver a informação de pacote fora de ordem
            System.out.println(
                    "Mensagem id " + id +
                            " recebida fora de ordem, ainda não recebidos os identificadores " +
                            notReceived
            );

            // Mesmo que tenha recebido fora de ordem, envia o ACK do recebimento (repetição seletiva)
            sendAck(seg, serverSocket);
            return;
        }

        // Chegou na ordem, entregar para a camada de aplicação
        deliverAvailableMessages(seg, serverSocket);
    }

    private static synchronized void deliverAvailableMessages(SegmentoConfiavel seg, DatagramSocket serverSocket) throws IOException {
        int id = seg.getNumeroSequencia();

        // Adiciona o número de sequência na lista de recebidos
        receivedIds.add(id);

        // Avança o número de sequência esperado para o próximo
        nextSequenceNumber++;

        // Funcionalidade 7: Exibe na console do receiver a informacao de pacote recebido na ordem
        System.out.println(
                "Mensagem id " + id +
                        " recebida na ordem, entregando para a camada de aplicação."
        );

        sendAck(seg, serverSocket);

        // Funcionalidade 6: verifica se há pacotes no buffer que agora podem ser entregues em ordem
        while (bufferOutOfOrder.containsKey(nextSequenceNumber)) {
            SegmentoConfiavel segBuffered = bufferOutOfOrder.remove(nextSequenceNumber);
            int idBuffered = segBuffered.getNumeroSequencia();

            // Já havia sido adicionado anteriormente, mas, para diminuir risco de intermitência,
            // adicionamos novamente na lista de ids recebidos (como é um Hashset, não há duplicatas)
            receivedIds.add(idBuffered);

            // Para cada pacote que já estava no buffer que pode ser enviado, incrementar o valor esperado
            // Para o próximo número de sequência
            nextSequenceNumber++;

            // Funcionalidade 7: Exibe na console do receiver a entrega do pacote drenado do buffer
            System.out.println(
                    "Mensagem id " + idBuffered +
                            " recebida na ordem, entregando para a camada de aplicação."
            );
        }
    }
}
