package io.github.matheusmoselli.reliable_comunication;

import java.io.Serializable;
import java.net.InetAddress;

// Funcionalidade 1: Cria um SegmentoConfiavel preenchendo o cabeçalho com número de sequência,
// flag de ACK, dados, timestamp de envio, porta e endereço IP de destino
public class SegmentoConfiavel implements Serializable {
    public SegmentoConfiavel() { }

    /**
     * Número de sequência (Id) da mensagem
     */
    private int numeroSequencia;

    /**
     * Número de sequência que o ACK está confirmando o recebimento
     */
    private int numeroAck;

    /**
     * Se 'true', o tipo de segmento é ACK, caso contrário é uma mensagem
     */
    private boolean isAck;

    /**
     * Mensagem a ser enviada
     */
    private String dados;

    /**
     * Momento (em milissegundos) em que a mensagem foi enviada
     */
    private long timestampEnvio;

    /**
     * Endereço de IP do 'sender' da mensagem
     */
    private InetAddress enderecoIP;

    /**
     * Porta do 'sender' da mensagem
     */
    private Integer porta;

    /**
     * Getter do número de sequência
     * @return número de sequência
     */
    public int getNumeroSequencia() { return numeroSequencia; }

    /**
     * Setter do número de sequência
     */
    public void setNumeroSequencia(int numeroSequencia) { this.numeroSequencia = numeroSequencia; }

    /**
     * Getter do número de sequência que o ACK representa
     * @return número do ACK
     */
    public int getNumeroAck() { return numeroAck; }

    /**
     * Setter do número do ACK
     */
    public void setNumeroAck(int numeroAck) { this.numeroAck = numeroAck;  }

    /**
     * Getter da mensagem
     * @return mensagem
     */
    public String getDados() {
        return dados;
    }

    /**
     * Setter da mensagem
     */
    public void setDados(String dados) {
        this.dados = dados;
    }

    /**
     * Getter do momento de envio
     * @return momento de envio (em milissegundos)
     */
    public long getTimestampEnvio() {
        return timestampEnvio;
    }

    /**
     * Setter do momento de envio
     */
    public void setTimestampEnvio(long timestampEnvio) {
        this.timestampEnvio = timestampEnvio;
    }

    /**
     * Getter do 'isAck'
     * @return se é Ack (true) ou não (false)
     */
    public boolean getIsAck() {
        return isAck;
    }

    /**
     * Setter do 'isAck'
     */
    public void setIsAck(boolean ack) {
        isAck = ack;
    }

    /**
     * Getter do endereço de IP que enviou a mensagem
     * @return endereço de IP
     */
    public InetAddress getEnderecoIP() { return enderecoIP; }

    /**
     * Setter do endereço de IP
     */
    public void setEnderecoIP(InetAddress enderecoIP) { this.enderecoIP = enderecoIP; }

    /**
     * Getter da porta que enviou a mensagem
     * @return porta
     */
    public Integer getPorta() { return porta; }

    /**
     * Setter da porta
     */
    public void setPorta(Integer porta) { this.porta = porta; }
}