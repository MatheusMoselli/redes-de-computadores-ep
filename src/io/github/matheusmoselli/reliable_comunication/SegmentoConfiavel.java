package io.github.matheusmoselli.reliable_comunication;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.Serializable;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;

public class SegmentoConfiavel implements Serializable {
    public SegmentoConfiavel() { }

    public SegmentoConfiavel(DatagramSocket socket) throws IOException, ClassNotFoundException {
        byte[] buffer = new byte[65535]; // 2^16 - Maior tamanho possível para um UDP
        DatagramPacket recPkt = new DatagramPacket(buffer, buffer.length);
        socket.receive(recPkt);

        ByteArrayInputStream bais = new ByteArrayInputStream(recPkt.getData());
        ObjectInputStream ois = new ObjectInputStream(bais);
        SegmentoConfiavel seg = (SegmentoConfiavel) ois.readObject();

        this.numeroSequencia = seg.getNumeroSequencia();
        this.numeroAck = seg.getNumeroAck();
        this.isAck = seg.getIsAck();
        this.dados = seg.getDados();
        this.timestampEnvio = seg.getTimestampEnvio();
        this.porta = recPkt.getPort();
        this.enderecoIP = recPkt.getAddress();
    }

    private int numeroSequencia;

    private int numeroAck;

    private boolean isAck;

    private String dados;

    private long timestampEnvio;

    private InetAddress enderecoIP;

    private Integer porta;

    public int getNumeroSequencia() { return numeroSequencia; }

    public void setNumeroSequencia(int numeroSequencia) { this.numeroSequencia = numeroSequencia; }

    public int getNumeroAck() { return numeroAck; }

    public void setNumeroAck(int numeroAck) { this.numeroAck = numeroAck;  }

    public String getDados() {
        return dados;
    }

    public void setDados(String dados) {
        this.dados = dados;
    }

    public long getTimestampEnvio() {
        return timestampEnvio;
    }

    public void setTimestampEnvio(long timestampEnvio) {
        this.timestampEnvio = timestampEnvio;
    }

    public boolean getIsAck() {
        return isAck;
    }

    public void setIsAck(boolean ack) {
        isAck = ack;
    }

    public InetAddress getEnderecoIP() { return enderecoIP; }

    public void setEnderecoIP(InetAddress enderecoIP) { this.enderecoIP = enderecoIP; }

    public Integer getPorta() { return porta; }

    public void setPorta(Integer porta) { this.porta = porta; }
}