package io.github.matheusmoselli.reliable_comunication;

import java.io.*;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.util.*;

public class Sender {
    /**
     * Variável criada para facilitar leitura dos ‘inputs’ do usuário
     */
    private static final BufferedReader inFromUser = new BufferedReader(new InputStreamReader(System.in));

    /**
     * Representa o número de sequência (Id) do próximo pacote a ser enviado (inicia em 0)
     */
    private static Integer currentSequenceNumber = 0;

    /**
     * Lista de números de sequências (Ids) cujos ACKs já foram recebidos
     * Funcionalidades 2 e 5: Implementar o tratamento de pacotes perdidos (uso de temporizador e timeout)
     */
    private static final Set<Integer> acknowledgedPackages = new HashSet<>();

    /**
     * Lista de SegmentoConfiavel que foram enviados { numSeq: segmentoConfiavel }
     * Funcionalidades 2 e 5: Implementar o tratamento de pacotes perdidos (uso de temporizador e timeout)
     */
    private static final Map<Integer, SegmentoConfiavel> sentPackages
            = Collections.synchronizedMap(new HashMap<>());

    /**
     * Tempo de timeout (se demorar mais que 3 segundos para um ACK chegar, considerar como timeout)
     */
    private static final int TIMEOUT_MS = 3 * 1000;

    /**
     * Tempo utilizado no tipo de envio 'atraso' (5s, ou seja, mais que o tempo de timeout)
     */
    private static final int SLOW_DELAY_MS = TIMEOUT_MS + (2 * 1000);

    /**
     * Porta que o receiver está instanciado. Utilizada para envio de mensagens
     */
    private static Integer receiverPort;

    /**
     * Endereço IP que o receiver está instanciado. Valor padrão de 127.0.0.1 (local)
     */
    private static InetAddress ipAddress;

    public static void main(String[] args) throws Exception {
        // Funcionalidade 8: Captura de IP e porta via teclado (pressionar Enter usa o valor default)
        getIPAddressAndPortNumberFromUser();

        // Cria o 'socket' que será responsável por receber os ACKs e enviar as mensagens
        DatagramSocket clientSocket = new DatagramSocket();

        // Cria a Thread que vai ser responsável por receber os ACKs das mensagens enviadas
        AckListenerThread ackListener = new AckListenerThread(clientSocket);
        ackListener.start();

        // Cria a Thread que vai ser responsável por controlar o reenvio dos pacotes perdidos/atrasados
        TimeoutThread timeoutThread = new TimeoutThread(clientSocket);
        timeoutThread.start();

        try {
            // 'Loop' infinito para envio contínuo de mensagens em um 'Sender'
            // Para sair do 'Loop', basta escolher um tipo de envio inválido
            while (true) {
                // Recebe a próxima mensagem do usuário e o tipo de envio
                UserInputDTO userInput = getUserInput();

                // Se o tipo de envio for inválido, o processo será encerrado
                if (userInput == null) return;

                // Envia a mensagem conforme o tipo de envio selecionado
                send(clientSocket, userInput.getUserMessage(), userInput.getTransmissionType());
            }
        } finally {
            // Encerra a Thread responsável por receber os ACKs
            ackListener.interrupt();

            // Encerra a Thread responsável por controle dos reenvios
            timeoutThread.interrupt();

            // Encerra as operações do 'socket'
            clientSocket.close();
        }
    }

    /**
     * Função responsável em obter o número de IP e a porta a partir do 'input' do usuário
     * @throws IOException Exceção em caso de erro
     */
    private static void getIPAddressAndPortNumberFromUser() throws IOException {
        System.out.print("Digite o IP do receiver [default: 127.0.0.1]: ");
        String ipInput = inFromUser.readLine();

        // Se o valor inserido for vazio (ou seja, apenas o Enter foi pressionado), utilizar 127.0.0.1
        if (ipInput.isEmpty()) ipInput = "127.0.0.1";
        ipAddress = InetAddress.getByName(ipInput);

        System.out.print("Digite a porta do receiver [default: 9876]: ");

        // Se o valor inserido for vazio (ou seja, apenas o Enter foi pressionado), utilizar 9876
        String portInput = inFromUser.readLine();
        receiverPort = portInput.isEmpty() ? 9876 : Integer.parseInt(portInput);
    }

    /**
     * Função que envia o pacote para o 'receiver'
     * @param socket 'socket' que irá enviar a mensagem
     * @param message mensagem a ser enviada
     * @param transmissionType tipo de envio
     * @throws Exception Exceção em caso de erro
     */
    private static void send(DatagramSocket socket, String message, String transmissionType) throws Exception {
        // Cria o pacote original a partir da mensagem do usuário
        SegmentPacketDTO segPkt = createSegment(message);
        SegmentoConfiavel seg = segPkt.getOriginalSegment();
        int id = seg.getNumeroSequencia();

        // Se o tipo de envio for fora de ordem, já cria o pacote que irá ser enviado antes do correto
        SegmentPacketDTO outOfOrderPkt = null;
        if (transmissionType.equals("fora de ordem")) {
            // Cria o pacote que será enviado antes do original
            outOfOrderPkt = createSegment(message);

            System.out.println("Mensagem \"" +
                    outOfOrderPkt.getOriginalSegment().getDados() +
                    "\" enviada como fora de ordem com id " +
                    outOfOrderPkt.getOriginalSegment().getNumeroSequencia());
        }

        System.out.println(
                "Mensagem \"" + message + "\" enviada como " + transmissionType + " com id " + id
        );

        switch (transmissionType) {
            case "lenta":
                // Funcionalidade 5: Tratamento de pacote lento
                // Simula um envio lento (após 5 segundos)
                sentPackages.put(seg.getNumeroSequencia(), seg);
                Thread.sleep(SLOW_DELAY_MS);
                socket.send(segPkt.getSendPacket());
                break;

            case "perda":
                // Funcionalidade 2: Tratamento de pacote perdido
                // Simula a perda de um pacote (registra que enviou, mas não envia)
                sentPackages.put(seg.getNumeroSequencia(), seg);
                break;

            case "duplicada":
                // Funcionalidade 4: Tratamento de pacote duplicado
                // Simula a duplicação de um pacote (envia o mesmo pacote duas vezes)
                socket.send(segPkt.getSendPacket());
                sentPackages.put(seg.getNumeroSequencia(), seg);
                socket.send(segPkt.getSendPacket());
                break;

            case "fora de ordem":
                // Funcionalidade 3: Tratamento de pacote fora de ordem
                // Simula um envio de pacotes fora de ordem utilizando o pacote fora de ordem criado anteriormente
                socket.send(outOfOrderPkt.getSendPacket());
                sentPackages.put(outOfOrderPkt.getOriginalSegment().getNumeroSequencia(), outOfOrderPkt.getOriginalSegment());
                socket.send(segPkt.getSendPacket());
                sentPackages.put(seg.getNumeroSequencia(), seg);
                break;
            default:
                // Realiza o envio normal
                socket.send(segPkt.getSendPacket());
                sentPackages.put(seg.getNumeroSequencia(), seg);
                break;
        }
    }

    /**
     * Função para obter a mensagem e o tipo de envio
     * @return um objeto com as informações: mensagem e tipo de envio
     * @throws IOException Exceção em caso de erro
     */
    private static UserInputDTO getUserInput() throws IOException {
        // Lê o próximo 'input' do usuário e salva na mensagem
        System.out.print("Digite a mensagem: ");
        String userMessage = inFromUser.readLine();

        // Lê o próximo 'input' do usuário e salva no tipo de envio
        System.out.println("Escolha o tipo de envio:");
        System.out.println("  [1] normal        - envio padrão");
        System.out.println("  [2] lenta         - atraso maior que o timeout (reenvio automático)");
        System.out.println("  [3] perda         - pacote nao e enviado (simula perda na rede)");
        System.out.println("  [4] duplicada     - pacote enviado duas vezes");
        System.out.println("  [5] fora de ordem - dois pacotes enviados em ordem invertida");
        System.out.println("  qualquer outro valor encerra o programa");

        System.out.print("Opção escolhida: ");
        String transmissionType = inFromUser.readLine();

        // Tipos de envios válidos
        List<String> validTypes = Collections.unmodifiableList(
                Arrays.asList("lenta", "perda", "fora de ordem", "duplicada", "normal")
        );

        // Se o tipo de envio for inválido, encerra o programa
        if (!validTypes.contains(transmissionType)) {
            System.out.println("Tipo de envio inválido, encerrando processo.");
            return null;
        }

        return new UserInputDTO(userMessage, transmissionType);
    }

    /**
     * Função para criar o SegmentoConfiavel e o pacote a partir da mensagem
     * @param mensagem mensagem a ser enviada para o 'receiver'
     * @return um objeto com o 'SegmentoConfiavel' e com o pacote a ser enviado
     * @throws IOException Exceção em caso de erro
     */
    private static SegmentPacketDTO createSegment(String mensagem) throws IOException {
        // Funcionalidade 1: Cria um SegmentoConfiavel preenchendo o cabeçalho com número de sequência,
        // flag de ACK, dados, timestamp de envio, porta e endereço IP de destino
        SegmentoConfiavel seg = new SegmentoConfiavel();
        seg.setNumeroSequencia(currentSequenceNumber++);
        seg.setIsAck(false);
        seg.setDados(mensagem);
        seg.setTimestampEnvio(System.currentTimeMillis());
        seg.setPorta(receiverPort);
        seg.setEnderecoIP(ipAddress);

        return prepararPacote(seg);
    }

    /**
     * Função que transforma o SegmentoConfiavel em pacote de bytes a ser enviado
     * @param seg SegmentoConfiavel que deve ser transformado em bytes para envio
     * @return o DatagramPacket a ser enviado
     * @throws IOException Exceção em caso de erro
     */
    private static SegmentPacketDTO prepararPacote(SegmentoConfiavel seg) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ObjectOutputStream oos = new ObjectOutputStream(baos);
        oos.writeObject(seg);
        byte[] sendData = baos.toByteArray();

        DatagramPacket pkt = new DatagramPacket(sendData, sendData.length, ipAddress, receiverPort);
        return new SegmentPacketDTO(seg, pkt);
    }

    /**
     * Thread responsável por receber os ACKs
     */
    private static class AckListenerThread extends Thread {
        private final DatagramSocket socket;

        public AckListenerThread(DatagramSocket socket) {
            this.socket = socket;

            // Se essa for a última thread (ou código) em execução, permite o fechamento do programa sem erros
            setDaemon(true);
        }

        /**
         * Chamado automaticamente no Thread.start()
         */
        @Override
        public void run() {
            // Enquanto a Thread não for interrompida inesperadamente, fica esperando os ACKs
            while (!isInterrupted()) {
                try {
                    // Fica TRAVADO esperando o próximo ACK
                    SegmentoConfiavel ack = receber(socket);

                    // Valida se o pacote recebido realmente é um ACK
                    if (ack.getIsAck()) {
                        // Recebe o número de sequência referente ao pacote cujo recebimento está sendo confirmado
                        int idConfirmado = ack.getNumeroAck();

                        synchronized (acknowledgedPackages) {
                            // Funcionalidade 6: Atualiza o buffer de ACKs recebidos com o id confirmado
                            acknowledgedPackages.add(idConfirmado);
                        }

                        // Funcionalidade 7: Exibe na console do sender a confirmação de recebimento pelo receiver
                        System.out.println("Mensagem id " + idConfirmado + " recebida pelo receiver.");
                    }
                } catch (Exception ignored) {
                    break;
                }
            }
        }
    }

    /**
     * Thread responsável pelo controle de timeout das mensagens enviadas
     */
    private static class TimeoutThread extends Thread {
        private final DatagramSocket socket;

        public TimeoutThread(DatagramSocket socket) {
            this.socket = socket;

            // Se essa for a última thread (ou código) em execução, permite o fechamento do programa sem erros
            setDaemon(true);
        }

        /**
         * Chamado automaticamente no Thread.start()
         */
        @Override
        public void run() {
            // Enquanto a Thread não for interrompida inesperadamente, fica esperando os ACKs
            while (!isInterrupted()) {
                try {
                    int CHECK_FREQUENCY_IN_MS = 100;
                    Thread.sleep(CHECK_FREQUENCY_IN_MS);

                    long now = System.currentTimeMillis();

                    // Funcionalidade 6: Percorre o buffer de pacotes enviados para verificar timeouts
                    synchronized (sentPackages) {
                        for (SegmentoConfiavel seg : sentPackages.values()) {
                            long timeWaiting = now - seg.getTimestampEnvio();

                            // Funcionalidades 2 e 5: Se o tempo de espera ultrapassou o timeout e o ACK
                            // ainda não foi recebido, reenvia o pacote (trata perda e pacote lento)
                            if (timeWaiting > TIMEOUT_MS && !acknowledgedPackages.contains(seg.getNumeroSequencia())) {
                                // Funcionalidade 7: Exibe na console do sender o aviso de timeout
                                System.out.println("Mensagem id " +
                                        seg.getNumeroSequencia() +
                                        " deu timeout, reenviando.");

                                seg.setTimestampEnvio(now);

                                SegmentPacketDTO pkt = prepararPacote(seg);
                                socket.send(pkt.getSendPacket());
                            }
                        }
                    }

                } catch (Exception ignored) {
                    break;
                }
            }
        }
    }

    /**
     * Classe para facilitar o manuseio da mensagem a ser enviada e o tipo do envio
     */
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

    /**
     * Classe para facilitar o manuseio do SegmentoConfiavel e seu respectivo pacote
     */
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

    /**
     * Função para converter os bytes recebidos em SegmentoConfiavel
     * @param socket 'socket' que recebeu a informação
     * @return SegmentoConfiavel convertido
     * @throws IOException Exceção em caso de erro
     * @throws ClassNotFoundException Exceção em caso de erro
     */
    public static SegmentoConfiavel receber(DatagramSocket socket) throws IOException, ClassNotFoundException {
        // Cria o buffer para ler os bytes recebidos
        byte[] buffer = new byte[65535]; // 2^16 - Maior tamanho possível para um UDP
        DatagramPacket recPkt = new DatagramPacket(buffer, buffer.length);

        // Recebe a mensagem -- TRAVA A EXECUÇÃO
        socket.receive(recPkt);

        // Converte os bytes em SegmentoConfiavel
        ByteArrayInputStream bais = new ByteArrayInputStream(recPkt.getData());
        ObjectInputStream ois = new ObjectInputStream(bais);
        SegmentoConfiavel seg = (SegmentoConfiavel) ois.readObject();

        // Preenche o IP e porta reais de origem a partir do DatagramPacket,
        // garantindo que o receiver consiga responder com ACK mesmo em reenvios
        seg.setEnderecoIP(recPkt.getAddress());
        seg.setPorta(recPkt.getPort());

        return seg;
    }
}