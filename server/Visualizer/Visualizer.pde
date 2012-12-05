//
//  Visualizer.pde
//  Ilias Karim
//  Music 250A, CCRMA, Stanford University
//

import oscP5.*;
import netP5.*;
import ddf.minim.*;

// incoming OSC port for swarm.py
static final int oscPort = 57121;
// NOTE: numbers of boids are hard-coded in GridRenderer :(

OscP5 oscP5;
Minim minim;
AudioSource source;
GridRenderer gridRenderer;
int select;
 
boolean sketchFullScreen() {
  return false;
}

void setup()
{
  oscP5 = new OscP5(this, oscPort);

  size(1920, 1080);
    
  minim = new Minim(this);
  AudioSource source = minim.getLineIn(); 
  
  gridRenderer = new GridRenderer(source);
  
  source.addListener(gridRenderer);
}
 
void draw()
{
  gridRenderer.draw();
}
 

void oscEvent(OscMessage msg) 
{  
  String pattern = msg.addrPattern();

  Random random = new Random();
    
    
  //if (random.nextInt() % 2 == 0) {
    
  //}
  
  //gridRenderer.setRGB(random.nextFloat(), random.nextInt(), random.nextFloat());


  // handle OSC messages from SuperCollider
  if (pattern.equals("/jerk")) {
    gridRenderer.setMode(random.nextInt() % 2);
    
    if (random.nextInt() % 5 == 0) {
      gridRenderer.setRGB(1, 1, 1);
      gridRenderer.setRGB(1, 1, 0);
      gridRenderer.setRGB(1, 0, 1);
      gridRenderer.setRGB(0, 1, 1);
      gridRenderer.setRGB(1, 0, 0);
      gridRenderer.setRGB(0, 1, 0);
      gridRenderer.setRGB(0, 0, 1);
    }
    
    // TODO: random color
    // TODO: toggle mode
    // TODO: cycle radius
  }
  // handle OSC messages from swarm.py
  else if (pattern.equals("/boid")) {
    int i = int(msg.get(0).intValue());
    //print("\n" + i + "\n");
    gridRenderer.boids[i][0] = msg.get(1).floatValue();
    gridRenderer.boids[i][1] = msg.get(2).floatValue();
    //gridRenderer.boids = boids;
  }  
  
  /*
  // handle OSC messages from PD
  if (pattern.equals("/radius")) {
    int val = msg.get(0).intValue();
    gridRenderer.r = val;
  }
  
  else if (pattern.equals("/rgb")) {
    float rVal = msg.get(0).intValue() / 128.;
    float gVal = msg.get(1).intValue() / 128.;
    float bVal = msg.get(2).intValue() / 128.;
    gridRenderer.setRGB(rVal, gVal, bVal);
  }
  
  else if (pattern.equals("/intensity")) {
    gridRenderer._alpha = msg.get(0).floatValue();
  }
  
  else if (pattern.equals("/mode")) {
    gridRenderer.setMode(msg.get(0).intValue());
  }*/
  
  // debug
  //print(msg);
}

void stop()
{
  source.close();
  minim.stop();
  super.stop();
}


