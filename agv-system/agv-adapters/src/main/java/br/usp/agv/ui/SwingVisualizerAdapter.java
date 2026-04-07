package br.usp.agv.ui;

import br.usp.agv.model.Agv;
import br.usp.agv.model.Order;
import br.usp.agv.model.Position;
import br.usp.agv.ports.outbound.WorldObserverPort;

import javax.swing.*;
import java.awt.*;
import java.util.ArrayList;
import java.util.List;

import java.awt.geom.Point2D;
import java.util.HashMap;
import java.util.Map;

/**
 * Adaptador de Saída que renderiza o estado do sistema usando Swing com interpolação suave.
 */
public class SwingVisualizerAdapter extends JFrame implements WorldObserverPort {

    private final int rows;
    private final int cols;
    private final int cellSize = 40;
    
    private List<Agv> agvs = new ArrayList<>();
    private List<Order> orders = new ArrayList<>();
    private br.usp.agv.model.Route activeRoute = null;
    
    // Posições visuais para interpolação (x, y em pixels flutuantes)
    private final Map<String, Point2D.Double> visualPositions = new HashMap<>();
    private final float lerpFactor = 0.15f; // Quão rápido a UI segue o core (0.0 a 1.0)

    public SwingVisualizerAdapter(int rows, int cols) {
        this.rows = rows;
        this.cols = cols;
        
        setTitle("AGV System Visualizer (Smooth)");
        setSize(cols * cellSize + 20, rows * cellSize + 40);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLocationRelativeTo(null);
        
        JPanel panel = new JPanel() {
            @Override
            protected void paintComponent(Graphics g) {
                super.paintComponent(g);
                // Ativar Anti-aliasing para ficar mais bonito
                Graphics2D g2 = (Graphics2D) g;
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                
                drawGrid(g2);
                drawOrders(g2);
                drawRoute(g2);
                drawAgvs(g2);
            }
        };
        
        add(panel);
        
        // Loop de Animação (aprox 60 FPS)
        Timer timer = new Timer(16, e -> {
            updateVisualPositions();
            repaint();
        });
        timer.start();
        
        setVisible(true);
    }

    private void updateVisualPositions() {
        for (Agv agv : agvs) {
            String id = agv.getAgvId();
            Position target = agv.getCurrentPosition();
            
            // Alvo em pixels (lembrando que row=x, col=y)
            double targetX = target.y() * cellSize;
            double targetY = target.x() * cellSize;
            
            Point2D.Double current = visualPositions.computeIfAbsent(id, 
                k -> new Point2D.Double(targetX, targetY));
            
            // LERP: current = current + (target - current) * factor
            current.x += (targetX - current.x) * lerpFactor;
            current.y += (targetY - current.y) * lerpFactor;
        }
    }

    private void drawAgvs(Graphics2D g) {
        g.setColor(Color.BLUE);
        for (Agv agv : agvs) {
            Point2D.Double p = visualPositions.get(agv.getAgvId());
            if (p == null) continue;
            
            g.fillOval((int)p.x + 5, (int)p.y + 5, cellSize - 10, cellSize - 10);
            g.drawString(agv.getAgvId(), (int)p.x + 10, (int)p.y + 25);
        }
    }

    private void drawRoute(Graphics2D g) {
        if (activeRoute == null || activeRoute.waypoints().isEmpty()) return;
        
        g.setColor(new Color(0, 150, 255, 80));
        g.setStroke(new BasicStroke(2, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        
        for (int i = 0; i < activeRoute.waypoints().size() - 1; i++) {
            Position from = activeRoute.waypoints().get(i);
            Position to = activeRoute.waypoints().get(i + 1);
            g.drawLine(
                from.y() * cellSize + cellSize / 2, from.x() * cellSize + cellSize / 2,
                to.y() * cellSize + cellSize / 2, to.x() * cellSize + cellSize / 2
            );
        }
    }

    private void drawGrid(Graphics2D g) {
        g.setColor(new Color(230, 230, 230));
        for (int i = 0; i <= rows; i++) {
            g.drawLine(0, i * cellSize, cols * cellSize, i * cellSize);
        }
        for (int j = 0; j <= cols; j++) {
            g.drawLine(j * cellSize, 0, j * cellSize, rows * cellSize);
        }
    }

    private void drawOrders(Graphics2D g) {
        for (Order order : orders) {
            // Pickup - Verde
            g.setColor(new Color(46, 204, 113));
            Position p = order.pickup();
            g.fillRoundRect(p.y() * cellSize + 10, p.x() * cellSize + 10, cellSize - 20, cellSize - 20, 10, 10);
            
            // Delivery - Vermelho
            g.setColor(new Color(231, 76, 60));
            Position d = order.delivery();
            g.drawRoundRect(d.y() * cellSize + 5, d.x() * cellSize + 5, cellSize - 10, cellSize - 10, 10, 10);
        }
    }

    @Override
    public void onAgvMoved(String agvId, Position newPosition) {
        repaint();
    }

    @Override
    public void onOrderCreated(Order order) {
        this.orders.add(order);
        repaint();
    }

    @Override
    public void onRouteCalculated(String agvId, br.usp.agv.model.Route route) {
        this.activeRoute = route;
        repaint();
    }

    @Override
    public void onSystemStateChanged(List<Agv> allAgvs, List<Order> pendingOrders) {
        this.agvs = new ArrayList<>(allAgvs);
        this.orders = new ArrayList<>(pendingOrders);
        repaint();
    }
}
