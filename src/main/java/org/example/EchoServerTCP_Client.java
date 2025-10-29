package org.example;

import java.io.*;
import java.net.*;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.example.TokenService.Token;

public class EchoServerTCP_Client {

    public static void main(String[] args) {

        Socket clientSocket = null;
        DataInputStream in = null;
        PrintStream out = null;
        DataInputStream teclado = new DataInputStream(System.in);
        Gson gson = new Gson();
        Token tokenAtual = new Token();

        try {
            System.out.print("Qual o IP do servidor? ");
            String serverIP = teclado.readLine();

            System.out.print("Qual a Porta do servidor? ");
            int serverPort = Integer.parseInt(teclado.readLine());

            System.out.println("Tentando conectar com host " + serverIP + " na porta " + serverPort);

            clientSocket = new Socket(serverIP, serverPort);
            in = new DataInputStream(clientSocket.getInputStream());
            out = new PrintStream(clientSocket.getOutputStream());

            System.out.println("Conectado!");

            while (true) {
                System.out.print("\nDigite a operacao (CRIAR USUARIO, LOGIN , LOGOUT, EDITAR PROPRIO USUARIO, EXCLUIR PROPRIO USUARIO), LISTAR PROPRIO USUARIO: ");
                String operacao = teclado.readLine().toUpperCase().replace(" ", "_");
                System.out.println(operacao);
                String jsonParaEnviar;

                switch (operacao) {
                    case "LOGIN":
                        System.out.print("Digite o nome de usuario: ");
                        String nome = teclado.readLine();
                        System.out.print("Digite a senha: ");
                        String senha = teclado.readLine();

                        JsonObject requisicaoLogin = new JsonObject();
                        requisicaoLogin.addProperty("operacao", operacao);
                        requisicaoLogin.addProperty("usuario", nome);
                        requisicaoLogin.addProperty("senha", senha);
                        jsonParaEnviar = gson.toJson(requisicaoLogin);
                        break;

                    case "CRIAR_USUARIO":
                        System.out.print("Digite o nome de usuario: ");
                        String nomeNovo = teclado.readLine();
                        System.out.print("Digite a senha: ");
                        String senhaNova = teclado.readLine();

                        JsonObject requisicaoCriarUsuario = new JsonObject();
                        requisicaoCriarUsuario.addProperty("operacao", operacao);
                        JsonObject usuarioJson = new JsonObject();
                        usuarioJson.addProperty("nome", nomeNovo);
                        usuarioJson.addProperty("senha", senhaNova);
                        requisicaoCriarUsuario.add("usuario", usuarioJson);

                        jsonParaEnviar = gson.toJson(requisicaoCriarUsuario);
                        break;

                    case "LISTAR_PROPRIO_USUARIO":
                        if(tokenAtual == null) {
                            System.out.println("ERRO: Você precisa fazer login antes de realizar esta operacao.");
                            continue;
                        }

                        JsonObject requisicaoListar = new JsonObject();
                        requisicaoListar.addProperty("operacao", operacao);
                        requisicaoListar.addProperty("token", tokenAtual.getValor());
                        jsonParaEnviar = gson.toJson(requisicaoListar);
                        break;

                    case "EDITAR_PROPRIO_USUARIO":
                        if (tokenAtual == null) {
                            System.out.println("ERRO: Você precisa fazer login antes de realizar esta operacao.");
                            continue;
                        }

                        System.out.print("Digite a nova senha: ");
                        String senhaAtualizada = teclado.readLine();

                        JsonObject atualizarUsuario = new JsonObject();
                        atualizarUsuario.addProperty("operacao", operacao);
                        atualizarUsuario.addProperty("token", tokenAtual.getValor());

                        JsonObject usuarioAtualizado = new JsonObject();
                        usuarioAtualizado.addProperty("senha", senhaAtualizada);
                        atualizarUsuario.add("usuario", usuarioAtualizado);

                        jsonParaEnviar = gson.toJson(atualizarUsuario);
                        break;

                    case "EXCLUIR_PROPRIO_USUARIO":
                        if(tokenAtual == null) {
                            System.out.println("ERRO: Voce precisa fazer login antes de realizar esta operacao.");
                            continue;
                        }

                        JsonObject requisicaoDelete = new JsonObject();
                        requisicaoDelete.addProperty("operacao", operacao);
                        requisicaoDelete.addProperty("token", tokenAtual.getValor());
                        jsonParaEnviar = gson.toJson(requisicaoDelete);
                        return;

                    case "LOGOUT":
                        System.out.println("Encerrando conexão e fazendo logout...");
                        JsonObject requisicaoLogout = new JsonObject();
                        requisicaoLogout.addProperty("operacao", operacao);

                        if (tokenAtual != null) {
                            requisicaoLogout.addProperty("token", tokenAtual.getValor());
                        }

                        out.println(gson.toJson(requisicaoLogout));
                        return;

                    default:
                        System.out.println("operacao invalida. Tente novamente.");
                        continue;
                }


                System.out.println("\n[CLIENTE -> SERVIDOR]");
                System.out.println(jsonParaEnviar);
                out.println(jsonParaEnviar);

                String respostaJsonString = in.readLine();
                System.out.println("\n[SERVIDOR -> CLIENTE]");
                System.out.println(respostaJsonString);

                JsonObject respostaJson = JsonParser.parseString(respostaJsonString).getAsJsonObject();

                if (respostaJson.has("token")) {
                    String valorDoToken = respostaJson.get("token").getAsString();
                    tokenAtual.setValor(valorDoToken);
                    System.out.println("-> Token recebido");
                }

                if (respostaJson.has("mensagem")) {
                    System.out.println("Mensagem: " + respostaJson.get("mensagem").getAsString());
                }

                if (respostaJson.has("usuario")) {
                    System.out.println("Nome do Usuário: " + respostaJson.get("usuario").getAsString());
                }


            }

        } catch (UnknownHostException e) {
            System.err.println("Host desconhecido.");
        } catch (IOException e) {
            System.err.println("Erro de conexao: IP ou Porta nao existe ou o servidor nao esta no ar");
        } catch (Exception e) {
            System.err.println("Ocorreu um erro inesperado: " + e.getMessage());
        } finally {
            System.out.println("Fechando todos os recursos...");
            try {
                if (out != null) out.close();
                if (in != null) in.close();
                if (clientSocket != null) clientSocket.close();
                if (teclado != null) teclado.close();
            } catch (IOException e) {
                System.err.println("Erro ao fechar os recursos: " + e.getMessage());
            }
        }
    }
}